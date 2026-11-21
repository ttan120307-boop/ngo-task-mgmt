# Log-in and role design (for the report)

## 1. Table ACCOUNT (14th relation)
ACCOUNT(volunteer_id PK/FK -> VOLUNTEER ON DELETE CASCADE ON UPDATE CASCADE, username UNIQUE, salt, password_hash, is_active, created_at, last_login)

Functional dependencies: volunteer_id -> all attributes; username -> volunteer_id (username is a candidate key).
Both determinants are candidate keys, so ACCOUNT is in **BCNF**. The role is NOT stored (it would be derivable data / a transitive dependency); it is computed at log-in:
- ADMIN   : VOLUNTEER.system_role = 'DIRECTOR'
- MANAGER : owns a PROJECT, or has a current MEMBERSHIP with team_role_id = 1 (Team Leader)
- USER    : every other active volunteer

## 2. New business rules
- BR25: one account per volunteer; username unique, case-insensitive, 3-60 characters of A-Z a-z 0-9 . _ -
- BR26: only accounts with is_active = 1 can log in (a wrong user name and a wrong password give the same message)
- BR27: permissions (matrix in README section 6) are enforced by the Java layer `ngo.auth.Authz`, called from every write method of the DAO/service classes.

## 3. Password storage
password_hash = SHA-256(salt || password), 64 hex characters, salt = 32 random hex characters per account (MySQL: `SHA2(CONCAT(salt,password),256)`). Comparison is constant time (`MessageDigest.isEqual`). Only PreparedStatements are used. Failed log-ins are delayed 0.5 s and locked for 10 s after 5 failures.

## 4. Limitations (write them in "Known Limitations")
1. Permissions are checked in the application; MySQL still sees ONE shared database user (config/db.properties). Per-user MySQL privileges / row-level security are future work.
2. SHA-256 with salt is acceptable for a course project; production systems should use bcrypt/Argon2.
3. All demo accounts share the password Ngo@2026; change it before any real use.
4. No account-management screen yet: new accounts are created with SQL (module 09 shows how). Future work: admin screen, password reset, audit log.
5. Not run against a live MySQL by the author of this patch: run `sql/14_account_constraint_tests.sql` and the screens once and keep screenshots.
