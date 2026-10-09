# Deploying Evan Fitness (free): Render + Aiven + Brevo

About 20–30 minutes, no credit card needed. You'll create three free accounts:

| Service | What it does | Free plan |
|---|---|---|
| **Aiven** | Hosts the MySQL database | Always free, 1 GB |
| **Brevo** | Sends the app's emails (reset, confirmation, weekly summary) | 300 emails/day |
| **Render** | Runs the app and gives it a web address | Free (sleeps after 15 idle minutes) |

Render's free plan blocks normal email (SMTP), which is why email goes through Brevo's web API.

Keep a notepad open: you'll copy a few values from each site and paste them into Render at the end.
**Never put these values in GitHub.** They only go into Render's settings.

---

## 1. Database: Aiven (about 5 minutes)

1. Go to **https://aiven.io** and sign up (Google or GitHub sign-in is fine).
2. Click **Create service** → choose **MySQL**.
3. Pick the **Free** plan and a region close to you (e.g. a US East region), then **Create service**.
4. Wait until the status says **Running** (a few minutes).
5. On the service's **Overview** page, copy these into your notepad:
   - **Host** (looks like `mysql-xxxx-yourname.aivencloud.com`)
   - **Port** (a number like `12345`)
   - **User** (`avnadmin`)
   - **Password** (click the eye icon). Don't paste it anywhere but Render, and don't share
     screenshots of the **Service URI**: it contains the password. If it ever leaks, reset it under
     **Users → avnadmin → Reset password** and update `DB_PASSWORD` in Render.
6. **Create the app's database.** Left menu → **Databases** → **Create database** → name it `fitness`
   (lowercase) → **Create**. It should now be listed next to `defaultdb`.
7. **Turn off the "primary key required" rule.** Left menu → **Service settings** → **Advanced
   configuration** → **Configure** → **Add configuration options** → `mysql.sql_require_primary_key`
   → set it **off** → **Save configuration**. (The app's very first migration creates one table before
   giving it a key; without this step the first deploy fails with
   `Unable to create or change a table without a primary key`.)
8. Build your **DB_URL** from the host and port, exactly like this (keep `?sslMode=REQUIRED`, it encrypts the connection):
   ```
   jdbc:mysql://HOST:PORT/fitness?sslMode=REQUIRED
   ```

The app creates all its tables automatically the first time it starts.

## 2. Email: Brevo (about 5 minutes)

1. Go to **https://www.brevo.com** and sign up for the free plan.
2. Verify your sender address: go to **Senders, domains & dedicated IPs** (under your account or settings menu) → **Senders** → **Add a sender**:
   - Name: `Evan Fitness`
   - Email: the Gmail address you want emails to come from (e.g. `evan.lindsay223@gmail.com`)
   - Open the confirmation email Brevo sends to that inbox and click the link.
3. Create an API key: **SMTP & API** → **API Keys** → **Generate a new API key**, name it `Evan Fitness`.
   Copy it right away (it's shown once). This is your **BREVO_API_KEY**.
4. Your **MAIL_FROM** is: `Evan Fitness <your-verified-address@gmail.com>`

Emails sent from a @gmail.com address through Brevo can sometimes land in spam. If that happens a lot, a cheap
custom domain (about $10/year) verified in Brevo fixes it.

## 3. App: Render (about 10 minutes, plus a 5–10 minute first build)

1. Go to **https://render.com** and sign up **with GitHub**.
2. Allow Render to access the **FitnessApp2024** repository when it asks.
3. Click **New** → **Blueprint** → pick **evanrobert/FitnessApp2024**. Render reads `render.yaml` from the repo.
4. It asks for the secret values. Paste them in:

   | Setting | Value |
   |---|---|
   | `DB_URL` | the URL from step 1.8 |
   | `DB_USERNAME` | `avnadmin` |
   | `DB_PASSWORD` | the Aiven password |
   | `APP_BASE_URL` | `https://evan-fitness.onrender.com` (see the note below) |
   | `BREVO_API_KEY` | the key from step 2.3 |
   | `MAIL_FROM` | `Evan Fitness <your-verified-address@gmail.com>` |

5. Click **Apply**. The first build takes 5–10 minutes. Watch **Logs**. It's ready when you see
   `Started ApplicationFitnessApplication`.

**About `APP_BASE_URL`:** Render names the address after the service (`https://evan-fitness.onrender.com`). If that
name is taken, Render adds a few characters. After the first deploy, check the address at the top of the
service page. If it's different, go to **Environment**, update `APP_BASE_URL` to match, and save (Render
redeploys automatically). Email links use this address, so it must match exactly.

**Optional: see how people use the app.** In Render → **Environment**, add `ADMIN_USERNAMES` with your
own username (several: separate with commas) and save. A **Usage** tab then appears under Settings for
you only: how many members log, how often, and how many seconds it takes. Totals only, no names or
content; events are deleted after 180 days.

## 4. Check it works

1. Open your `https://….onrender.com` address. (After 15 idle minutes on the free plan the first visit takes
   30–60 seconds to wake up. That's normal.)
2. **Create an account** with your real email. The confirmation email should arrive within a minute. Click it.
3. Log a workout, a meal and the 3 sleep & mood questions.
4. Log out → **Forgot password?** → you should get a reset email.
5. Settings → Account & security → **Send me a preview** to see the weekly summary.
6. On your phone, open the site and use the **Put Evan Fitness on your home screen** card on Today
   (iPhone: Share → Add to Home Screen). Log in with **Keep me signed in on this device** ticked.

If an email doesn't arrive, open Render → **Logs** and search for `could not be sent:`. The text after it says why
(for example, the sender address isn't verified in Brevo yet).

## If a deploy fails

Open Render → **Logs** and look at the last 20–30 lines.

| Log says | Fix |
|---|---|
| `Unable to create or change a table without a primary key` | Step 1.7: turn off `mysql.sql_require_primary_key` in Aiven, then **Manual Deploy → Deploy latest commit**. |
| `Unknown database 'fitness'` | Step 1.6: create the `fitness` database in Aiven → **Databases**. Check `DB_URL` ends in `/fitness?sslMode=REQUIRED`. |
| `Access denied for user 'avnadmin'` | `DB_PASSWORD` in Render doesn't match Aiven. Copy it again (or reset it in Aiven) and save. |
| `Communications link failure` | `DB_URL` host or port is wrong, or the Aiven service is powered off (free services pause when unused; power it on). |
| `refuses to start` / `APP_BASE_URL` | `APP_BASE_URL` must be your `https://…onrender.com` address. |

## Updating the app later

Every merge to `main` on GitHub redeploys automatically (`autoDeploy: true`). Your data stays in Aiven.

## Upgrading to stop the sleeping (about $7/month)

Render → your service → **Settings** → **Instance Type** → **Starter** → Save. Same address, same data, no
other changes. (Paid Render plans can also send email over SMTP, but Brevo keeps working, so there's nothing to change.)

## Security checklist

- Secrets live only in Render's **Environment** settings, never in GitHub.
- The app runs with the `prod` profile: HTTPS-only cookies, HSTS, per-visitor sign-in limits, no emails in the logs.
  It refuses to start if the database login is missing or `APP_BASE_URL` isn't an `https://` address.
- The database connection is encrypted (`sslMode=REQUIRED`). Use the long password Aiven generates.
- GitHub's Dependabot (`.github/dependabot.yml`) opens a pull request when a library gets a security update.
- This stores personal wellness data (weight, sleep, mood). It's fine for you and friends. Don't put clients' or
  patients' data on free hosting without your compliance team's approval.
- "Keep me signed in" is opt-in per device; changing your password signs out every kept device.
- The usage report (if you set `ADMIN_USERNAMES`) stores only that something was logged and how fast, never
  what; it's deleted after 180 days.
- Back up now and then: each member can download their data as CSV (Settings → Export data).
