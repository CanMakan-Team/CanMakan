import { CredentialLoginForm } from '../shared/ui/CredentialLoginForm'
import { CanMakanMascot, LoginBrand } from '../shared/ui/CanMakanMascot'
import { USER_REGISTER_PATH } from '../app/userPortalPaths'

/** USER portal login — live email/password against POST /api/auth/login. */
export function UserLoginPage() {
  return (
    <main className="login-page login-page--family">
      <div className="login-composition login-composition--family">
        <section className="family-login-introduction" aria-labelledby="family-entry-title">
          <LoginBrand />
          <CanMakanMascot pose="wave" size="large" className="login-greeting-mascot" />
          <p className="eyebrow">Welcome</p>
          <h1 id="family-entry-title">Dietary support for you, and for your family when you choose.</h1>
          <p>
            Look after your own profile at your own pace. A Family Circle is here
            whenever household tools would help.
          </p>
        </section>

        <section className="login-card" aria-labelledby="family-login-title">
          <p className="eyebrow">User Portal</p>
          <h2 id="family-login-title">Sign in</h2>
          <p>Sign in with the email and password for your CanMakan account.</p>
          <CredentialLoginForm
            portal="FAMILY"
            expectedRole="ROLE_APP_USER"
            destination="/family"
            buttonLabel="Enter CanMakan"
            buttonClassName="button--primary"
            registerPath={USER_REGISTER_PATH}
          />
        </section>
      </div>
    </main>
  )
}
