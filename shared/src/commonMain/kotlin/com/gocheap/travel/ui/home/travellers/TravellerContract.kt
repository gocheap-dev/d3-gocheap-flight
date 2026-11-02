package com.gocheap.travel.ui.home.travellers

import com.gocheap.travel.data.model.CabinClass

/**
 * What the traveller page collects and hands back: the party split the way an airline fares it,
 * and the cabin it is flying in.
 *
 * The page used to collect one number, which could only ever go out as adults. A child sits in a
 * seat and an infant on a lap, and the two are priced differently from an adult and from each
 * other, so the split belongs in the search rather than in a footnote.
 */
data class TravellerSelection(
    val adults: Int = MinAdults,
    val children: Int = 0,
    val infants: Int = 0,
    val cabinClass: CabinClass = CabinClass.Economy,
) {
    val count: Int get() = adults + children + infants

    /** An airline will not seat more than this on one booking; bigger parties are split up. */
    val canAddAdult: Boolean get() = adults < MaxAdults && count < MaxTravellers

    val canAddChild: Boolean
        get() = count < MaxTravellers && children + infants < MaxChildrenAndInfants

    /**
     * An infant flies on an adult's lap, so there can never be more of them than there are laps.
     */
    val canAddInfant: Boolean get() = canAddChild && infants < adults

    val canRemoveAdult: Boolean get() = adults > MinAdults
    val canRemoveChild: Boolean get() = children > 0
    val canRemoveInfant: Boolean get() = infants > 0

    companion object {
        const val MinAdults = 1
        const val MaxAdults = 8

        /** One booking cannot carry a whole coach party; airlines split those into several. */
        const val MaxTravellers = 15
        const val MaxChildrenAndInfants = 7

        /**
         * Pulls a party inside the limits above, so a selection cannot be built in a state the
         * search would not accept — whichever side it arrived from, a saved search, a route
         * argument, or the page itself.
         */
        fun of(
            adults: Int,
            children: Int = 0,
            infants: Int = 0,
            cabinClass: CabinClass = CabinClass.Economy,
        ): TravellerSelection {
            val clampedAdults = adults.coerceIn(MinAdults, MaxAdults)
            val clampedChildren = children.coerceIn(0, MaxChildrenAndInfants)
            return TravellerSelection(
                adults = clampedAdults,
                children = clampedChildren,
                // Infants give way to children when both are over the shared ceiling, and never
                // outnumber the laps they fly on.
                infants = infants.coerceIn(0, minOf(clampedAdults, MaxChildrenAndInfants - clampedChildren)),
                cabinClass = cabinClass,
            )
        }
    }
}

/**
 * The party as one route argument: adults, children, infants and the cabin, split by [Separator].
 *
 * So "2-1-1-Business" is two adults, a child and an infant in business. A compact string rather
 * than four path segments because this also travels back through saved state, and one key that
 * either arrives whole or not at all cannot be read half-written.
 */
internal fun TravellerSelection.encodeTravellers(): String =
    listOf(adults, children, infants, cabinClass.name).joinToString(Separator)

/**
 * The inverse, falling back to a single adult in economy for anything unreadable — the same shrug
 * the other pickers take, and the page has to open onto something either way.
 */
internal fun decodeTravellerSelection(raw: String?): TravellerSelection {
    val fields = raw?.split(Separator) ?: return TravellerSelection()
    if (fields.size != 4) return TravellerSelection()
    val adults = fields[0].toIntOrNull() ?: return TravellerSelection()
    val children = fields[1].toIntOrNull() ?: return TravellerSelection()
    val infants = fields[2].toIntOrNull() ?: return TravellerSelection()
    val cabin = CabinClass.entries.firstOrNull { it.name == fields[3] } ?: CabinClass.Economy
    return TravellerSelection.of(adults, children, infants, cabin)
}

private const val Separator = "-"
