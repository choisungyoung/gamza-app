import SwiftUI
import ComposeApp
import GoogleSignIn

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.doInitKoin(
            supabaseUrl: "https://pwolqtdutnrjaqalnzna.supabase.co",
            supabaseAnonKey: "sb_publishable_8ZrMpsZMjeDoMgVIfWiTjw_L-QeAwJd",
            googleWebClientId: "194931040394-qgcni3q23kpdf7tfi93j6o5pcfun2h0h.apps.googleusercontent.com"
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
