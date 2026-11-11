# Signing

Android only installs an update over an existing app when it's signed by the same key.

- `hermes-keys.p12.gpg` is a PKCS12 keystore (alias `hermes`) encrypted with GPG (AES-256). Its
  password, which also opens the keystore, exists only as the repository secret
  **`SIGNING_PASSWORD`** (Settings → Secrets and variables → Actions). Without it the file is useless.
- The first CI run that has the secret but no keystore creates the key, encrypts it with the secret
  and commits only the encrypted file. Don't change the secret afterwards: the old key couldn't be
  opened any more, and new builds couldn't update installed copies.
- CI builds the release APK unsigned, then signs it with `apksigner`.
- If the secret is missing, CI still builds and tests, and publishes `Hermes-debug.apk` (signed with
  the runner's throwaway debug key) so there's something to try. Debug builds from different runs
  can't update each other; uninstall first. Once the secret is set, `Hermes.apk` is the one to use.

Never commit the decrypted keystore or the password.
