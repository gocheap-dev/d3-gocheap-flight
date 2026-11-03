package com.gocheap.flights.utils

/**
 * Everything this app is configured with that is not a user preference.
 *
 * Grouped by the backend each value belongs to rather than by what kind of value it is: a host and
 * the paths hung off it are read together — see the HTTP clients and the deeplink builders — and
 * keeping them apart is how a path ends up appended to the wrong base URL. The four groups that
 * are not backends ([App], [Defaults], [Support]) follow the same rule for the same reason.
 *
 * The per-environment values are not written here. [Support.PrivacyPolicyUrl] and
 * [Support.Email] come from local.properties through the `generateBuildValues` task in
 * `shared/build.gradle.kts`, as do the four base URLs — which live in the generated `BaseUrls`
 * rather than in this object, because the HTTP clients and deeplink builders read them directly.
 * Pointing a checkout at a different environment is therefore a local change rather than a
 * committed one; absent a value there, each falls back to what it has always held.
 *
 * Still `const` throughout: the generated values are themselves compile-time constants, so these
 * inline exactly as literals do.
 */
object Constants {

    /** The stay supplier: autocomplete over its catalogue, and the results page it renders. */
    object Hotel {
        /** Type-ahead for stay destinations: cities, airports and individual properties. */
        const val AutocompletePath = "api/v4/autocomplete/"

        /**
         * The supplier localises its results. The reference pulls these off the stored region and
         * the app's install route; with no preference store here they stay at the same fallbacks
         * it uses.
         */
        /** The results page the supplier renders the whole search on. */
        const val DeeplinkPath = "deeplink/new/"
    }

    /** The rental supplier — same shape as [Hotel], different host and different parameters. */
    object Car {
        const val AutocompletePath = "api/v3/autocomplete/"

        /** [Hotel.DeeplinkPath]'s equivalent: same path, different host and parameters. */
        const val DeeplinkPath = "deeplink/new/"
    }

    /**
     * The flight host, which carries more than the other two: as well as autocomplete and a
     * deeplink it serves [ControllerPath] and the in-app search endpoints under [Search].
     */
    object Flight {
        /** Type-ahead for flight endpoints: cities and the airports inside them. */
        const val AutocompletePath = "api/v3/autocomplete/"

        /**
         * The flight supplier's deeplink. A different path from the other two — "-v2" and all —
         * because this one takes its legs as a JSON array rather than as flat parameters.
         */
        const val DeeplinkPath = "deeplink-v2/new/"

        /**
         * The switches the backend holds over this build, resolved per country, app and route. On
         * the flight host rather than the user one, as in the reference app — see `AppController`.
         */
        const val ControllerPath = "controller/"

        /**
         * Opens an in-app flight search and answers with the id the results are polled on. A POST:
         * the legs, the cabin and the party travel as a body, everything else as query parameters.
         *
         * Only reached when the controller's `new_tab` says API — see
         * `AppController.flightSearchMode`.
         */
        const val SearchPath = "api/v3/search/"

        /**
         * One page of results for a search already opened. Polled repeatedly against the same id:
         * the backend fills its cache as suppliers answer, so the same page comes back fuller each
         * time.
         */
        const val PollPath = "api/v3/poll/"

        /**
         * Releases a search the user has walked away from, so the backend can stop collecting for
         * it. Best-effort — leaving it would only cost the backend a cache entry it drops on its
         * own.
         *
         * Unversioned, unlike the two above: this is the path the endpoint actually lives at.
         */
        const val DeleteSearchPath = "api/search/delete/"

        /** How the poll loop over [PollPath] is paced. */
        object Search {
            /**
             * How long a flight search or poll may take before it is given up on.
             *
             * Well past the 15s the flight client's own timeout allows, and set per request rather
             * than raising that one: the client is shared with flight autocomplete, where 15s is
             * deliberate — a type-ahead answer slower than that is already stale. A search is not,
             * so it gets its own.
             */
            const val TimeoutMillis = 45_000L

            /**
             * How many itineraries a poll asks for at a time.
             *
             * 20, matching the reference app. Large enough that the first page fills the screen
             * before the list can be scrolled off the end of it, small enough that it arrives while
             * the backend is still collecting rather than after.
             */
            const val PollPageSize = 20

            /**
             * How long to wait between polls of a search that is still collecting.
             *
             * The reference app's interval. The backend fills its cache over a few seconds, so this
             * is roughly how often there is anything new to show.
             */
            const val PollIntervalMillis = 1_000L
        }
    }

    /**
     * The inline ads shown alongside flight results, served by the supplier's own ad host rather
     * than by any of this publisher's three.
     *
     * Two calls in sequence and a third that is fire-and-forget: a session is opened once and
     * reused, every ad request quotes its id, and an ad that reaches the screen reports back.
     */
    object Ads {

        /**
         * `GET ads/session` — opens an ad session and answers with the `sid` every later call
         * quotes. Takes the country and a `label`, which is this install's user id.
         */
        const val SessionPath = "ads/session"

        /**
         * `POST ads/flight/list` — the ads for one trip. The trip travels as the body and the
         * session id as `_sid_`, which is the host's own spelling and not a choice made here.
         */
        const val FlightListPath = "ads/flight/list"

        /**
         * How long a session id is reused before a new one is opened.
         *
         * The reference app's twenty minutes. The host does not say when a session expires, so
         * this is a ceiling chosen below it rather than a value read from a response — an expired
         * id comes back as a 401, which is handled besides this. See `AdsRepository`.
         */
        const val SessionValidityMillis = 20L * 60L * 1_000L

        /**
         * What the host calls the party, one entry per traveller.
         *
         * Not the ages `api/v3/search/` takes — the same trip is described to the two endpoints in
         * two different vocabularies, and this is the ad host's. A lap infant is "seatInfant" here
         * even though it occupies no seat, which is the reference app's spelling of it.
         */
        const val Adult = "adult"
        const val Child = "child"
        const val Infant = "seatInfant"
    }

    /** This app's own backend: who the install is, and what it has done. */
    object User {
        const val AddUserPath = "users/add/"

        /**
         * Where an exit to a supplier is recorded. One row per search handed off, keyed on the
         * `user_id` [AddUserPath] produced — see `SessionTracking`.
         */
        const val StartSessionPath = "users/session/"

        /**
         * Where this install's FCM registration token is stored, so the backend has an address to
         * send a push to. Also keyed on the `user_id` [AddUserPath] produced — see
         * `PushTokenRegistration`.
         */
        const val UpdatePushTokenPath = "users/update-push-token/"
    }

    /**
     * What this app calls itself, to the backends and on screen.
     *
     * The per-platform values are split into [Android] and [Ios] rather than sitting side by side
     * with an `Ios` suffix, because every one of them is wrong on the other platform. Read them
     * through [com.gocheap.flights.data.user.platformAppCode],
     * [com.gocheap.flights.data.user.platformAnonymousUserId] and
     * [com.gocheap.flights.data.user.platformAppLabel] rather than directly: the callers are common
     * code that runs on both platforms, so a direct reference to either group is a value that is
     * right on one of them and wrong on the other.
     */
    object App {

        /**
         * The Android trio. Each has a counterpart in [Ios] and the two must move together.
         *
         * [Code] is what the supplier deeplinks and the autocomplete endpoints identify this app
         * as, one code per app per platform. The platform letter sits third: "D3AT" is
         * D3/Android/TFlight, so iOS is "D3IT". That reading comes from the reference app, whose
         * iOS `appCode` is "D1IF" — D1/iOS/Flight — against an Android sibling of "D1AF".
         * **Both confirmed against the staging backend:** `controller/` and `api/v3/autocomplete/`
         * each answer 200 for "D3IT" exactly as they do for "D3AT".
         *
         * [AnonymousUserId] is [Code] lowercased behind `fallback_`, and the two move together:
         * changing one without the other sends a user id that belongs to the other platform's app.
         *
         * [Label] is what `users/add` is told this install is, which the backend keys attribution
         * off. **Not [Code].** These are two different values with two different jobs: [Code]
         * ("D3AT") identifies the app to the supplier deeplinks, [Label] identifies it to the user
         * backend. The reference apps keep them equally separate — Flight70 sends `d1_ios_flight`
         * as its label while its `appCode` is "D1IF" — and swapping one for the other fails with
         * "Invalid app", as does a wrong or empty one.
         */
        object Android {
            const val Code = "D3AT"

            /** Sent in place of a real account id, until `users/add` produces one. */
            const val AnonymousUserId = "fallback_d3at"

            const val Label = "d3_android_tflight"
        }

        /**
         * The iOS counterparts of [Android], documented there.
         *
         * The Android label was already issued for this app. **The iOS one is confirmed**:
         * `users/add` accepts "d3_ios_tflight" against the staging backend and answers with a user
         * id, and `users/session/` accepts rows keyed on that id for all three verticals.
         */
        object Ios {
            const val Code = "D3IT"

            const val AnonymousUserId = "fallback_d3it"

            const val Label = "d3_ios_tflight"
        }

        /**
         * The product name the User-Agent on every user endpoint leads with, as the reference
         * app's `APPLICATION_NAME` does. Not the package id and not the display label — the
         * backend matches on this string, so it stays put even if either of those is renamed.
         */
        const val ApplicationName = "GoCheap"

        /**
         * What the app calls itself on screen.
         *
         * Deliberately not [ApplicationName], and the two now differ: that one is matched on by
         * the backend and did not move when the brand did, while this one is a label that may be
         * translated or rebranded without anything on the wire changing. They held the same text
         * until this was renamed, which is exactly the moment collapsing them would have cost a
         * User-Agent the backend no longer recognised.
         *
         * Used as the title over every supplier page the app embeds — see `ResultsWebView`. Those
         * pages are somebody else's, and the toolbar above them is the one thing on screen that
         * still belongs to this app, so it says so rather than repeating the route or naming the
         * seller.
         */
        const val DisplayName = "GoCheap: Flight Ticket Booking"
    }

    /**
     * Where a region has not been chosen yet. Once it has, the Profile tab's regional settings
     * carry it and these are only the first-launch values — see `AppSettings`.
     */
    object Defaults {
        const val Country = "US"
        const val Currency = "USD"
        const val Language = "en"
        const val Route = "organic"
    }

    /** Where the Profile tab's "Other" group leaves for, and what a share carries. */
    object Support {
        /**
         * The three addresses the Profile tab's "Other" group leaves for.
         *
         * Placeholders against the publisher's own domain: there is no privacy page or support
         * inbox for this build yet, and a row that opens nothing would be worse than one that
         * opens a holding page. Replace all three before release.
         *
         * [SupportLinks.PrivacyPolicyUrl] and [SupportLinks.Email] are configured as the base URLs are — out of
         * local.properties, falling back to the values below — so that pointing a build at a real
         * privacy page or a real inbox does not mean editing this file. [WebsiteUrl] is not: it is
         * the publisher's own domain and does not move between environments.
         */
        const val WebsiteUrl = "https://lascade.com"

        /** Subject line on the support mail, matching the SDK's own "App feedback". */
        const val Subject = "App feedback"

        /** What a share carries beside the store link. */
        const val ShareMessage =
            "Find cheap flights, stays and rental cars with ${App.DisplayName}:"

        /**
         * `mailto:` with the subject already filled in. Encoded by hand because this is the one URL
         * the app builds without Ktor's helpers in reach — and only the space needs it.
         */
        fun mailtoUrl(): String = "mailto:${SupportLinks.Email}?subject=${Subject.replace(" ", "%20")}"
    }
}
