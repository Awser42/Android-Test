package com.awser.counter

/** Native colors keep every palette available offline without bundled image assets. */
object CounterThemes {
    const val DEFAULT_ID = "emerald"

    data class Theme(
        val id: String,
        val name: String,
        val darkAccent: Int,
        val lightAccent: Int,
        val lcdTop: Int,
        val lcdBottom: Int,
        val lcdInk: Int,
    )

    val all: List<Theme> = listOf(
        Theme("emerald", "Emerald", 0xFF74DFA5.toInt(), 0xFF23754C.toInt(), 0xFF74E38F.toInt(), 0xFF4DCF71.toInt(), 0xFF113B1F.toInt()),
        Theme("jade", "Jade", 0xFF80DDB1.toInt(), 0xFF236E50.toInt(), 0xFF9AE8B5.toInt(), 0xFF68D998.toInt(), 0xFF163D2B.toInt()),
        Theme("mint", "Mint", 0xFF96E5C0.toInt(), 0xFF256B51.toInt(), 0xFFB6F1CF.toInt(), 0xFF86E0B3.toInt(), 0xFF173E30.toInt()),
        Theme("teal", "Teal", 0xFF75DBCC.toInt(), 0xFF166C63.toInt(), 0xFF85E6D5.toInt(), 0xFF50CDBA.toInt(), 0xFF0C3B34.toInt()),
        Theme("ocean", "Ocean", 0xFF7CCFE4.toInt(), 0xFF176780.toInt(), 0xFFA5DDED.toInt(), 0xFF75C3DC.toInt(), 0xFF153849.toInt()),
        Theme("sky", "Sky", 0xFF8ECFF9.toInt(), 0xFF246698.toInt(), 0xFFB5E0FE.toInt(), 0xFF84C9F4.toInt(), 0xFF183956.toInt()),
        Theme("indigo", "Indigo", 0xFFACB8FF.toInt(), 0xFF4650A3.toInt(), 0xFFCED4FF.toInt(), 0xFFA9B5F6.toInt(), 0xFF252C57.toInt()),
        Theme("violet", "Violet", 0xFFC5ACF8.toInt(), 0xFF674399.toInt(), 0xFFDACBFE.toInt(), 0xFFBEA9F0.toInt(), 0xFF392A55.toInt()),
        Theme("plum", "Plum", 0xFFDCB0E6.toInt(), 0xFF7B4381.toInt(), 0xFFE6C3EA.toInt(), 0xFFCD9CD5.toInt(), 0xFF4A2A4E.toInt()),
        Theme("rose", "Rose", 0xFFF2AECF.toInt(), 0xFF993D63.toInt(), 0xFFFACBDE.toInt(), 0xFFEFA1C0.toInt(), 0xFF53283C.toInt()),
        Theme("coral", "Coral", 0xFFF5B3A0.toInt(), 0xFF9A4C38.toInt(), 0xFFFFD0C2.toInt(), 0xFFF4AA98.toInt(), 0xFF542D25.toInt()),
        Theme("orange", "Orange", 0xFFF5C18E.toInt(), 0xFF955018.toInt(), 0xFFFFD8B0.toInt(), 0xFFF6BB87.toInt(), 0xFF542F1B.toInt()),
        Theme("amber", "Amber", 0xFFF2D08D.toInt(), 0xFF85601A.toInt(), 0xFFFFE7B0.toInt(), 0xFFF4CF82.toInt(), 0xFF4F3B17.toInt()),
        Theme("gold", "Gold", 0xFFE2D38E.toInt(), 0xFF796018.toInt(), 0xFFF2E7B8.toInt(), 0xFFDDCE89.toInt(), 0xFF46401D.toInt()),
        Theme("slate", "Slate", 0xFFB8CBDC.toInt(), 0xFF496278.toInt(), 0xFFD4DFE8.toInt(), 0xFFAEBDCD.toInt(), 0xFF293A48.toInt()),
        Theme("monochrome", "Monochrome", 0xFFD9DFDB.toInt(), 0xFF4D5D53.toInt(), 0xFFE7E9E7.toInt(), 0xFFC3C8C5.toInt(), 0xFF29302C.toInt()),
    )

    private val byId = all.associateBy { it.id }

    fun resolve(id: String): Theme = byId[id] ?: byId.getValue(DEFAULT_ID)
}
