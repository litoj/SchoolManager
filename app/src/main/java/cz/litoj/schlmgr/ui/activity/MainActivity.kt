package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUiSaveStateControl
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.navigation.NavigationView
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.ui.component.ExplorerSearchField
import cz.litoj.schlmgr.ui.viewmodel.AppBarViewModel
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.popup.CrashReportPopup
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.DialogHost
import kotlinx.coroutines.launch
import java.io.File

object EdgeToEdge {
    /**
     * Applies the system bar insets to the target view as padding.
     *
     * @param target the view to pad (typically the activity's content root)
     * @param includeTop `true` to also pad the top (status bar). Pass `false`
     * when the top inset is already handled elsewhere (e.g. by an
     * `AppBarLayout` with `fitsSystemWindows="true"`).
     */
    @JvmStatic
    fun apply(target: View, includeTop: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val bars: Insets =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                bars.left,
                if (includeTop) bars.top else 0,
                bars.right,
                bars.bottom
            )
            insets
        }
    }
}

class MainActivity : BaseActivity() {

    private lateinit var mAppBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private val appBar: AppBarViewModel by viewModels()

    /** The bar's 'more options' and 'search' buttons, rendered from [appBar]. */
    private var moreButton: ImageView? = null
    private var selectButton: ImageView? = null

    /**
     * The search bar view sharing the toolbar with the title. Dumb hosting
     * only: the rendered [ExplorerSearchField] talks straight to its
     * [ExplorerViewModel], so this shell never sees text, threshold or timing.
     */
    private var searchBarView: ComposeView? = null
    private var savedTitle: CharSequence? = null

    /** Whether this activity instance already showed the pending crash report. */
    private var crashShown: Boolean = false

    @OptIn(NavigationUiSaveStateControl::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The engine's AndroidIOSystem platform layer is gone: the repository, the
        // option statics and the crash handler come up here — before setContentView,
        // because the main fragment queries the database while it inflates.
        Startup.init(this)
        setContentView(R.layout.nav_menu)
        // The app's dialogs render through this host at the content root: each dialog
        // is its own window above the whole activity, whatever destination is shown,
        // and the shared dialog stack survives rotation without repaint machinery.
        // The view itself stays zero-size — only the Dialog windows are visible.
        (findViewById<ViewGroup>(android.R.id.content)).addView(ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
            setContent { DialogHost() }
        })

        // The top (status bar) inset is handled by the AppBarLayout itself
        // (fitsSystemWindows), so only the remaining insets are padded here.
        EdgeToEdge.apply(findViewById(R.id.include), false)
        // The drawer's NavigationView can't rely on fitsSystemWindows here: in
        // edge-to-edge mode the insets are consumed by the AppBarLayout sibling before
        // reaching it (dispatch order), so the drawer was clipped by the status bar.
        // Apply the system bar insets to it explicitly, including the top.
        EdgeToEdge.apply(findViewById(R.id.nav_menu), true)
        setSupportActionBar(findViewById(R.id.bar))
        moreButton = findViewById(R.id.bar_more)
        moreButton!!.setOnClickListener { v ->
            // the menu stays reachable while rows are selected: the select-all toggle
            // lives on the search button then (see AppBarViewModel.State.selectAsSelectAll)
            if (appBar.current.menuRes == 0) return@setOnClickListener
            val pm = PopupMenu(this, v)
            pm.inflate(appBar.current.menuRes)
            // A selection gives the menu its selection actions: flipping the selected
            // rows, referencing them elsewhere, and the export submenu (every export
            // writes what is selected). Everything else (new/sort/import) needs no
            // selection and hides while one is active.
            val selecting = appBar.current.selectAsSelectAll
            for (i in 0 until pm.menu.size()) {
                val item: MenuItem = pm.menu.getItem(i)
                val selectionAction = item.itemId == R.id.more_flip ||
                    item.itemId == R.id.more_reference ||
                    item.itemId == R.id.more_export
                item.isVisible = if (selectionAction) selecting else !selecting
            }
            appBar.currentDestination?.let { dest ->
                pm.setOnMenuItemClickListener(
                    dest::onMenuItemClick
                )
            }
            pm.show()
        }
        selectButton = findViewById(R.id.bar_select)
        selectButton!!.setOnClickListener {
            // while rows are selected the button is the select-all toggle, not the search
            if (appBar.current.selectAsSelectAll) {
                appBar.currentDestination?.onSelectAllToggle()
                return@setOnClickListener
            }
            appBar.currentDestination?.onSearchOpen()
        }
        // The bar views render from the ViewModel: the main fragment inflated during
        // setContentView above and may already have requested a menu/search state —
        // the flow replays the latest state to this collector, so requests made
        // before the views existed converge without a bind race.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appBar.state.collect { applyBar(it) }
            }
        }
        // The crash report of an unexpected failure from a previous session (the
        // former finishLoad hook) is shown once the window is attached — see
        // onPostResume; creating it here crashed on the not-yet-attached window.
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = AppBarConfiguration.Builder(
            R.id.menu_objects, R.id.test, R.id.menu_options, R.id.menu_about
        )
            .setOpenableLayout(findViewById(R.id.drawer_layout)).build()
        navController = Navigation.findNavController(this, R.id.content_main)
        NavigationUI.setupActionBarWithNavController(
            this,
            navController,
            mAppBarConfiguration
        )
        // No saved state for the drawer: its destinations are flat siblings, not
        // nested stacks. The default save/restore would make a click on 'Subjects'
        // restore the screen it was left from and silently do nothing.
        NavigationUI.setupWithNavController(
            findViewById<NavigationView>(R.id.nav_menu),
            navController,
            saveState = false
        )
    }

    /** Renders the remembered 'more'/'search' button state onto the bound bar views. */
    private fun applyBar(state: AppBarViewModel.State) {
        moreButton?.visibility =
            if (state.menuRes == 0) View.GONE else View.VISIBLE
        if (state.selectAsSelectAll) {
            selectButton?.setBackgroundResource(R.drawable.ic_check_all)
            selectButton?.visibility = View.VISIBLE
        } else {
            selectButton?.setBackgroundResource(R.drawable.ic_search)
            selectButton?.visibility =
                if (state.searchVisible) View.VISIBLE else View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) ||
            super.onSupportNavigateUp()
    }

    override fun onPostResume() {
        super.onPostResume()
        // Show the previous session's crash report once per launch, with the window
        // attached (a popup during onCreate dies on the missing window token, which
        // turned every relaunch into the same crash again).
        if (!crashShown) {
            crashShown = true
            val crash = Startup.getCrashReport()
            // a rotation recreates this activity while the popup is already on
            // the process-wide stack — the report shows once
            if (crash != null && !Dialogs.isShowing<CrashReportPopup>()) CrashReportPopup(crash)
        }
    }

    /**
     * Back reached the shell itself: no destination handled it (fragments
     * register their own callbacks, which the dispatcher checks first) — hand
     * the event to the system, which pops the nav stack or finishes.
     */
    override fun onUserBackPressed() {
        goBack()
    }

    // ------------------------------------------------------------ app-bar search bar
    //
    // Dumb toolbar hosting for the shared search field: attach/detach only.
    // The owning destination drives these from its view model's field state,
    // and the rendered field talks straight to that view model. Back reaches
    // the destination's own callback first, so no shell callback is needed.

    /**
     * Shows [viewModel]'s search field in place of the toolbar title. Ignored
     * while a bar is already shown — one field at a time.
     */
    fun showSearchBar(viewModel: ExplorerViewModel) {
        if (searchBarView != null) return
        val bar = findViewById<Toolbar>(R.id.bar)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
            setContent { ExplorerSearchField(viewModel) }
        }
        savedTitle = bar.title
        bar.title = null
        // the search bar is a plain toolbar child — it takes the title's place
        bar.addView(
            view, Toolbar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // the bar takes the icon's place too — hide it beyond the remembered state
        selectButton?.visibility = View.GONE
        searchBarView = view
    }

    /** Hides the search bar and restores the title and the search button. */
    fun hideSearchBar() {
        val view = searchBarView ?: return
        searchBarView = null
        val bar = findViewById<Toolbar>(R.id.bar)
        bar.removeView(view)
        bar.title = savedTitle
        applyBar(appBar.current) // re-applies the search button's remembered visibility
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroy() {
        val cacheFiles: Array<File>? = cacheDir.listFiles()
        if (cacheFiles != null) for (f in cacheFiles) if (!f.delete()) f.deleteOnExit()
        super.onDestroy()
    }
}
