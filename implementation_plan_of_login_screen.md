# Supabase Authentication & Device Session Management

Implement a complete, tactical-styled Login and Sign Up system authenticated against Supabase, with persistent on-device session management and a future-ready database schema for room/squad creation.

## User Review Required

> [!IMPORTANT]
> **Authentication Method**: Sign In and Sign Up will authenticate via direct Supabase PostgREST table endpoints (`users` table) using SHA-256 cryptographic password hashing. This keeps username/password simple without requiring email verification workflows.

> [!NOTE]
> **Persistent Device Session**: When a user registers or logs in on the device, their session (`isLoggedIn = true`, `userId`, `username`) is stored in `SessionManager` (`SharedPreferences`). On app relaunch, it automatically skips the auth screen and takes the user to the home screen with their username pre-filled. A Logout button is added to allow switching accounts.

---

## Proposed Changes

### 1. Supabase Database Schema (MCP Migration)

Apply a database migration via Supabase MCP to create:
- **`users` table**: `id (UUID PK)`, `username (TEXT UNIQUE)`, `password_hash (TEXT)`, `created_at`, `updated_at`.
- **`rooms` table** *(future upgrade compatible)*: `id (UUID PK)`, `room_code (VARCHAR UNIQUE)`, `room_name (TEXT)`, `created_by (FK users)`, `is_private (BOOLEAN)`, `password_hash (TEXT NULL)`, `max_participants (INT)`, `created_at`, `updated_at`.
- **`room_members` table** *(future upgrade compatible)*: `id (UUID PK)`, `room_id (FK rooms)`, `user_id (FK users)`, `role (VARCHAR)`, `joined_at`, `last_seen_at`.
- **Row Level Security (RLS) policies**: Configured for public anon access to allow registering and querying user credentials safely.

---

### 2. Android App Changes

#### [NEW] [SessionManager.kt](file:///d:/apps/android%20studio%20projects/Walkie-Talkie-App-Internet-Version/app/src/main/java/com/example/walkietalkieapp/auth/SessionManager.kt)
- Manages local device session in `SharedPreferences`.
- Provides `isLoggedIn`, `currentUsername`, `currentUserId`, `saveSession()`, and `clearSession()`.

#### [NEW] [SupabaseAuthManager.kt](file:///d:/apps/android%20studio%20projects/Walkie-Talkie-App-Internet-Version/app/src/main/java/com/example/walkietalkieapp/auth/SupabaseAuthManager.kt)
- Communicates with Supabase REST API (`https://crlfqcrhsjybrebbbaww.supabase.co/rest/v1/users`).
- Handles `signUp(username, password)`: checks duplicate username, hashes password, inserts user into Supabase.
- Handles `signIn(username, password)`: queries user record, verifies password hash, returns success/failure.

#### [NEW] [AuthScreen.kt](file:///d:/apps/android%20studio%20projects/Walkie-Talkie-App-Internet-Version/app/src/main/java/com/example/walkietalkieapp/ui/AuthScreen.kt)
- Tactical dark UI matching the app's aesthetic (`#0A0A0B`, `#00FF66` neon accents, `#1F1F24` borders, animated background parallax).
- Toggle tabs: **SIGN IN** and **SIGN UP**.
- Fields:
  - Username (with icon)
  - Password (with hide/show eye toggle)
  - Confirm Password (on Sign Up tab)
- Real-time input validation and loading spinners.
- Error alerts (e.g. "Username already taken", "Invalid password", "Password must be at least 6 characters").

#### [MODIFY] [MainActivity.kt](file:///d:/apps/android%20studio%20projects/Walkie-Talkie-App-Internet-Version/app/src/main/java/com/example/walkietalkieapp/MainActivity.kt)
- Check `SessionManager.isLoggedIn()` at launch.
- If not logged in -> render `AuthScreen`.
- If logged in -> render `JoinCreateScreen` (with pre-filled saved username) or `SquadScreen`.
- Add a Logout action in the top bar of `JoinCreateScreen` and in `SquadScreen` settings to clear session and return to `AuthScreen`.

---

## Verification Plan

### Database Verification (via Supabase MCP)
- Run `list_tables` to verify `users`, `rooms`, and `room_members` are created with correct columns, types, and constraints.

### Build Verification
- Compile and build APK with Gradle:
  ```powershell
  cmd.exe /c "set JAVA_HOME=D:\android studio\jbr&& set ANDROID_HOME=D:\android sdk&& gradlew.bat assembleDebug"
  ```

### Manual Flow Verification
1. Launch app on clean state -> shows `AuthScreen`.
2. Register a new user (e.g., `AlphaOne` / `squad123`) -> successfully created in Supabase `users` table -> auto-logs in to `JoinCreateScreen` with username `AlphaOne`.
3. Close app and relaunch -> automatically skips login and opens `JoinCreateScreen` with `AlphaOne` pre-filled.
4. Click Logout -> returns to `AuthScreen`.
5. Login with invalid password -> shows error alert.
6. Login with valid password -> logs in successfully.
