package cz.litoj.schlmgr.app

/** Test fixture for code that still creates its preferences through the process context. */
object AppSettings {
	private val current get() = Settings(GlobalDependencies.appContext)

	@JvmStatic fun getBool(key: String, def: Boolean): Boolean = current.getBool(key, def)
	@JvmStatic fun getInt(key: String, def: Int): Int = current.getInt(key, def)
	@JvmStatic fun getFloat(key: String, def: Float): Float = current.getFloat(key, def)
	@JvmStatic fun has(key: String): Boolean = current.has(key)
	@JvmStatic fun getString(key: String): String? = current.getString(key)
	@JvmStatic fun getString(key: String, def: String): String = current.getString(key, def)
	@JvmStatic fun set(key: String, value: Boolean) = current.set(key, value)
	@JvmStatic fun set(key: String, value: String) = current.set(key, value)
	@JvmStatic fun set(key: String, value: Int) = current.set(key, value)
	@JvmStatic fun set(key: String, value: Float) = current.set(key, value)
	@JvmStatic fun remove(key: String) = current.remove(key)
	@JvmStatic fun setNow(key: String, value: String) = current.setNow(key, value)
}
