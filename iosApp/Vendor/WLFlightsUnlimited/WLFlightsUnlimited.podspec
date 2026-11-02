# WLFlightsUnlimited is not in the public Travelpayouts SDK repository — tag 1.8.1, the newest
# there is, vendors WLFlights.xcframework and nothing else. It is handed out separately, so it
# cannot come down with `pod 'WLSDK/Flights'` and has to be vendored from inside this repository.
#
# It was first dropped straight into `iosApp/Pods/WLSDK/Vendor/Flights/`, which looks right and is
# not: `pod install` rebuilds that directory from CocoaPods' own clone of the tag, so the next run
# deletes it. `iosApp/Pods/` is also gitignored, so it was never committed either. Hence this: a
# local podspec over a copy that lives in the repository, which `pod install` links and embeds the
# same way it does every other pod, and which neither of those two things can take away.
#
# When Travelpayouts does ship it in a tagged release, delete this directory and the matching line
# in the Podfile — `pod 'WLSDK/Flights'` will bring it in on its own.
Pod::Spec.new do |s|
  s.name     = 'WLFlightsUnlimited'
  # Tracks the WLSDK release it was built against rather than a version of its own, which it does
  # not have: it is linked against those exact binaries and cannot be mixed with another set.
  s.version  = '1.8.1'
  s.summary  = 'Travelpayouts flights results screen, vendored separately from WLSDK.'
  s.homepage = 'https://github.com/travelpayouts/sdk-ios'
  s.license  = { :type => 'Commercial' }
  s.authors  = { 'Travelpayouts' => 'support@travelpayouts.com' }

  # A local pod still needs a source; this one is the directory the podspec is in.
  s.source = { :path => '.' }

  # What the binary itself was built for — `-target arm64-apple-ios14.0` in its .swiftinterface.
  # The Podfile's post_install hook raises this to the 15.0 floor Xcode 26 insists on.
  s.ios.deployment_target = '14.0'
  s.swift_version = '5.9'

  s.ios.vendored_frameworks = 'WLFlightsUnlimited.xcframework'

  # It imports WLFlights, WLCommon, WLNetwork and WLUserInterface, all of which are inside this
  # one subspec. Declared rather than left to the Podfile so the requirement travels with the
  # framework instead of being an accident of the two happening to be in the same target.
  s.dependency 'WLSDK/Flights'
end
