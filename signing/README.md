# Signing

The encrypted signing bundle uses the existing N2K02 signing setup and the `SIGNING_PASSPHRASE` Actions secret. The workflow decrypts it in a temporary directory, masks the keystore password, and removes decrypted files after the job.
