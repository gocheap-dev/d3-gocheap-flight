package com.gocheap.flights.navigation

sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object Onboarding : AppRoute("onboarding")

    /** The four destinations behind the floating bottom bar. */
    data object Home : AppRoute("home")
    data object Hotels : AppRoute("hotels")
    data object Cars : AppRoute("cars")
    data object Profile : AppRoute("profile")

    /** Pushed from Home and Profile rather than carried by the bottom bar. */
    data object MyTickets : AppRoute("my_tickets")
    data object Favorites : AppRoute("favorites")

    /**
     * The Profile tab's own pages. None of them answer back through saved state: they write to
     * the settings store, which every screen already reads from — see `SettingsViewModel`.
     */
    data object PriceDisplay : AppRoute("price_display")

    /** One page for all three regional lists; the kind both titles it and chooses its rows. */
    data object RegionPicker : AppRoute("region_picker") {
        const val KindArg = "kind"
        const val RouteWithArg = "region_picker/{$KindArg}"

        fun createRoute(kind: String): String = "region_picker/$kind"
    }

    data object AboutApp : AppRoute("about_app")

    /**
     * Flight results as the supplier's own page, opened when the controller's `new_tab` says
     * `KYK_DL`. No arguments: the screen reads the search back out of the recents store, as the
     * stay and rental results screens do.
     */
    data object FlightResults : AppRoute("flight_results")

    /**
     * Flight results drawn in-app, opened when the controller's `new_tab` says `KYK_API`.
     *
     * Argument-free, like [FlightResults] beside it: the screen reads the search back out of the
     * recents store rather than having it threaded through the route.
     */
    data object FlightApiResults : AppRoute("flight_api_results")

    /**
     * The booking site's checkout for a deal picked off [FlightApiResults].
     *
     * Also argument-free, and deliberately so — a deeplink is a URL, and [encodeRouteArg] rewrites
     * `/` to `_` and back, which corrupts any link containing an underscore. The deal travels
     * through `SelectedFlightDeal` instead.
     */
    data object FlightDeal : AppRoute("flight_deal")

    data object SearchResults : AppRoute("search_results") {
        const val FromArg = "fromCode"
        const val ToArg = "toCode"
        const val DateArg = "date"
        const val RouteWithArg = "search_results/{$FromArg}/{$ToArg}/{$DateArg}"

        fun createRoute(fromCode: String, toCode: String, date: String): String {
            return "search_results/$fromCode/$toCode/${date.encodeRouteArg()}"
        }
    }

    data object FlightDetail : AppRoute("flight_detail") {
        const val FlightIdArg = "flightId"
        const val RouteWithArg = "flight_detail/{$FlightIdArg}"

        fun createRoute(flightId: String): String = "flight_detail/$flightId"
    }

    data object SeatSelection : AppRoute("seat_selection") {
        const val FlightIdArg = "flightId"
        const val RouteWithArg = "seat_selection/{$FlightIdArg}"

        fun createRoute(flightId: String): String = "seat_selection/$flightId"
    }

    data object Checkout : AppRoute("checkout") {
        const val FlightIdArg = "flightId"
        const val SeatsArg = "seats"
        const val RouteWithArg = "checkout/{$FlightIdArg}/{$SeatsArg}"

        fun createRoute(flightId: String, seatLabels: List<String>): String {
            val seats = seatLabels.joinToString(separator = SeatSeparator).ifBlank { NoSeats }
            return "checkout/$flightId/$seats"
        }

        fun parseSeats(raw: String?): List<String> {
            if (raw.isNullOrBlank() || raw == NoSeats) return emptyList()
            return raw.split(SeatSeparator).filter { it.isNotBlank() }
        }

        private const val SeatSeparator = "-"
        private const val NoSeats = "none"
    }

    /**
     * Stay results. Argument-free: the page is driven by the whole search — destination ids
     * included — which the search card records on its way here, so squeezing a lossy copy of it
     * through route text would only give the screen a worse version of what it can already read.
     */
    data object HotelResults : AppRoute("hotel_results")

    /** Rental results, argument-free for the same reason as [HotelResults]. */
    data object CarResults : AppRoute("car_results")

    data object CarDetail : AppRoute("car_detail") {
        const val CarIdArg = "carId"
        const val RouteWithArg = "car_detail/{$CarIdArg}"

        fun createRoute(carId: String): String = "car_detail/$carId"
    }

    /**
     * The shared date picker, pushed from all three search cards. It hands its answer back through
     * the caller's saved state rather than a navigation argument, since arguments only travel
     * forwards.
     */
    data object Calendar : AppRoute("calendar") {
        const val ModeArg = "mode"
        const val TitleArg = "title"
        const val StartArg = "start"
        const val EndArg = "end"
        const val WithTimeArg = "withTime"
        const val StartTimeArg = "startTime"
        const val EndTimeArg = "endTime"
        const val SpanUnitArg = "spanUnit"
        const val RouteWithArg = "calendar/{$ModeArg}/{$TitleArg}/{$StartArg}/{$EndArg}/" +
            "{$WithTimeArg}/{$StartTimeArg}/{$EndTimeArg}/{$SpanUnitArg}"

        /** Keys the picked dates are written under on the previous back stack entry. */
        const val ResultStartKey = "calendar_result_start"
        const val ResultEndKey = "calendar_result_end"
        const val ResultStartTimeKey = "calendar_result_start_time"
        const val ResultEndTimeKey = "calendar_result_end_time"

        /** Route segments cannot be empty, so an absent date travels as this sentinel. */
        const val NoDate = "none"

        fun createRoute(
            mode: String,
            title: String,
            start: String?,
            end: String?,
            withTime: Boolean,
            startTime: String?,
            endTime: String?,
            spanUnit: String,
        ): String {
            return "calendar/$mode/${title.encodeRouteArg()}/" +
                "${(start ?: NoDate).encodeRouteArg()}/${(end ?: NoDate).encodeRouteArg()}/" +
                "$withTime/${startTime ?: NoDate}/${endTime ?: NoDate}/$spanUnit"
        }

        /** Turns a route argument back into a date string, or null for the sentinel. */
        fun readDate(raw: String?): String? {
            val decoded = raw?.decodeRouteArg()
            return decoded?.takeIf { it.isNotBlank() && it != NoDate }
        }
    }

    /**
     * The flight location picker, pushed from the search card for either end of a trip. Like
     * [Calendar], it answers through the caller's saved state rather than a navigation argument —
     * and like [CarLocationPicker], with the whole picked row, since the list comes from the
     * supplier rather than a catalogue this app ships.
     */
    data object LocationPicker : AppRoute("location_picker") {
        const val KindArg = "kind"
        const val TitleArg = "title"
        const val TargetArg = "target"
        const val ExcludeArg = "exclude"
        const val RouteWithArg =
            "location_picker/{$KindArg}/{$TitleArg}/{$TargetArg}/{$ExcludeArg}"

        const val ResultTargetKey = "location_result_target"
        const val ResultDestinationKey = "location_result_destination"

        /** Route segments cannot be empty, so an absent id travels as this sentinel. */
        const val NoId = "none"

        fun createRoute(
            kind: String,
            title: String,
            target: String,
            excludeId: String?,
        ): String {
            return "location_picker/$kind/${title.encodeRouteArg()}/$target/" +
                (excludeId ?: NoId).encodeRouteArg()
        }

        fun readId(raw: String?): String? {
            val decoded = raw?.decodeRouteArg()
            return decoded?.takeIf { it.isNotBlank() && it != NoId }
        }
    }

    /**
     * The stays destination page, pushed from the Hotels search card. Argument-free — the card has
     * only the one destination field — and it answers through the caller's saved state with the
     * whole picked row, for the same reason as [CarLocationPicker].
     */
    data object HotelDestinationPicker : AppRoute("hotel_destination") {
        const val ResultDestinationKey = "hotel_destination_result"
    }

    /**
     * The rental location page, pushed from the Cars search card for either end of a one-way
     * rental. Answers through the caller's saved state like [LocationPicker], but with the whole
     * picked row rather than an id: these come from the supplier's autocomplete, so there is no
     * local catalogue to resolve an id against.
     *
     * The target is the only argument — it both titles the page and says which field the answer
     * fills.
     */
    data object CarLocationPicker : AppRoute("car_location") {
        const val TargetArg = "target"
        const val RouteWithArg = "car_location/{$TargetArg}"

        const val ResultTargetKey = "car_location_result_target"
        const val ResultLocationKey = "car_location_result_location"

        fun createRoute(target: String): String = "car_location/$target"
    }

    /**
     * The guests-and-rooms page, pushed from the hotel search card. Answers via saved state.
     *
     * The rooms travel both ways as the one compact string `encodeRooms` writes, rather than as a
     * guest and a room count: the page now edits each room's party, and a pair of totals could not
     * carry it in either direction.
     */
    data object GuestPicker : AppRoute("guest_picker") {
        const val RoomsArg = "rooms"
        const val RouteWithArg = "guest_picker/{$RoomsArg}"

        const val ResultRoomsKey = "guest_result_rooms"

        fun createRoute(rooms: String): String = "guest_picker/$rooms"
    }

    /**
     * The travellers-and-cabin page, pushed from the flight search card. Answers via saved state.
     *
     * The party travels both ways as the one compact string `encodeTravellers` writes rather than
     * as a head count: the page now splits it into adults, children and infants and picks a cabin,
     * and a single number could not carry that in either direction.
     */
    data object TravellerPicker : AppRoute("traveller_picker") {
        const val PartyArg = "party"
        const val RouteWithArg = "traveller_picker/{$PartyArg}"

        const val ResultPartyKey = "traveller_result_party"

        fun createRoute(party: String): String = "traveller_picker/$party"
    }

    data object Ticket : AppRoute("ticket") {
        const val BookingIdArg = "bookingId"
        const val RouteWithArg = "ticket/{$BookingIdArg}"

        fun createRoute(bookingId: String): String = "ticket/$bookingId"
    }
}

/** Route segments cannot contain slashes; dates like "19, dec 2022" also need spaces escaped. */
private fun String.encodeRouteArg(): String = replace(" ", "+").replace("/", "_")

internal fun String.decodeRouteArg(): String = replace("+", " ").replace("_", "/")

/** Destinations that keep the floating bottom bar on screen. */
internal val BottomBarRoutes: Set<String> = setOf(
    AppRoute.Home.route,
    AppRoute.Hotels.route,
    AppRoute.Cars.route,
    AppRoute.Profile.route,
)
