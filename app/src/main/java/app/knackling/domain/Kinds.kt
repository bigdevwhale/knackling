package app.knackling.domain

/** A shelf of the catalogue: music, threads, words… */
data class Group(val id: String, val ru: String, val en: String) { val title get() = tr(ru, en) }

/** What a hobby can count besides minutes: pages, rows, songs… */
data class Unit(val id: String, val ru1: String, val ru2: String, val ru5: String, val en1: String, val enN: String) {
    fun of(n: Int) = count(n, ru1, ru2, ru5, en1, enN)
    val many get() = tr(ru5, enN)
}

/** A challenge the apprentice suggests. {|а} is a feminine ending, filled by [Say.g]. */
data class Idea(val ru: String, val en: String) { val text get() = Say.g(tr(ru, en)) }

/** A preset hobby with its own apprentice (the pet's species id is the hobby id). */
data class Kind(val id: String, val group: String, val ru: String, val en: String, val unit: String?, val petRu: String, val petEn: String,
                val color: Long, val ideas: List<Idea>) {
    val title get() = tr(ru, en)
    val pet get() = tr(petRu, petEn)
}

/** A free apprentice for a hobby of the user's own. */
data class OwnPet(val id: String, val ru: String, val en: String, val color: Long) { val name get() = tr(ru, en) }

object Kinds {
    val byId = Catalog.kinds.associateBy { it.id }
    val ownById = Catalog.own.associateBy { it.id }
    fun of(id: String): Kind? = byId[id]
    fun group(id: String) = Catalog.groups.first { it.id == id }
    /** The colour of a species (a preset hobby or a free apprentice). */
    fun color(species: String): Long = byId[species]?.color ?: ownById[species]?.color ?: 0xA08060
    /** The species' own name: «Струнка», «Рыжик». */
    fun petName(species: String): String = byId[species]?.pet ?: ownById[species]?.name ?: species
    val allSpecies: List<String> get() = Catalog.kinds.map { it.id } + Catalog.own.map { it.id }
}

/** Words that depend on how the user wants to be called: «мастер» or «мастерица». */
object Say {
    @Volatile var fem = false
    /** Fills «{|а}»-style marks: «понял{|а}» → «понял» or «поняла»; «{ся|ась}» too. */
    fun g(s: String): String = Regex("""\{([^|{}]*)\|([^|{}]*)\}""").replace(s) { if (fem) it.groupValues[2] else it.groupValues[1] }
    val master get() = if (fem) tr("мастерица", "maker") else tr("мастер", "maker")
}
