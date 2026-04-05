import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.doInitKoin(
            supabaseUrl: "https://pwolqtdutnrjaqalnzna.supabase.co",
            supabaseAnonKey: "sb_publishable_8ZrMpsZMjeDoMgVIfWiTjw_L-QeAwJd",
            googleWebClientId: "194931040394-qgcni3q23kpdf7tfi93j6o5pcfun2h0h.apps.googleusercontent.com"  // TODO: local.properties와 동일한 Web Client ID 입력
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
