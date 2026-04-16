import SwiftUI
import ComposeApp
import GoogleSignIn
import CryptoKit

@main
struct iOSApp: App {
    init() {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: "194931040394-8f015cfbp84fnhio2pc7tljjj140okl5.apps.googleusercontent.com",
            serverClientID: "194931040394-qgcni3q23kpdf7tfi93j6o5pcfun2h0h.apps.googleusercontent.com"
        )

        MainViewControllerKt.doInitKoin(
            supabaseUrl: "https://pwolqtdutnrjaqalnzna.supabase.co",
            supabaseAnonKey: "sb_publishable_8ZrMpsZMjeDoMgVIfWiTjw_L-QeAwJd",
            googleWebClientId: "194931040394-qgcni3q23kpdf7tfi93j6o5pcfun2h0h.apps.googleusercontent.com"
        )

        MainViewControllerKt.registerGoogleSignInStarter {
            DispatchQueue.main.async {
                let scenes = UIApplication.shared.connectedScenes
                let windowScene = scenes.first as? UIWindowScene
                guard let rootVC = windowScene?.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
                    MainViewControllerKt.onGoogleSignInResult(idToken: nil, rawNonce: nil, error: "No root view controller")
                    return
                }

                let rawNonce = randomNonceString()
                let hashedNonce = sha256(rawNonce)

                GIDSignIn.sharedInstance.signIn(
                    withPresenting: rootVC,
                    hint: nil,
                    additionalScopes: nil,
                    nonce: hashedNonce
                ) { result, error in
                    if let error = error {
                        MainViewControllerKt.onGoogleSignInResult(idToken: nil, rawNonce: nil, error: error.localizedDescription)
                    } else if let idToken = result?.user.idToken?.tokenString {
                        MainViewControllerKt.onGoogleSignInResult(idToken: idToken, rawNonce: rawNonce, error: nil)
                    } else {
                        MainViewControllerKt.onGoogleSignInResult(idToken: nil, rawNonce: nil, error: "ID 토큰을 받지 못했습니다")
                    }
                }
            }
        }
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

private func randomNonceString(length: Int = 32) -> String {
    var randomBytes = [UInt8](repeating: 0, count: length)
    SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
    return randomBytes.map { String(format: "%02x", $0) }.joined()
}

private func sha256(_ input: String) -> String {
    let inputData = Data(input.utf8)
    let hashed = SHA256.hash(data: inputData)
    return hashed.compactMap { String(format: "%02x", $0) }.joined()
}
