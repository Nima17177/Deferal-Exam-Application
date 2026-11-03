# DuckDuckDefer Gamification System 🎮

Welcome to the **DuckDuckDefer** Gamification guide! We have integrated a rewarding system designed to encourage timely exam deferrals, active participation from proctors, and consistent engagement from faculty.

---

## 🚀 Core Mechanics

### 1. Experience Points (XP) & Leveling
Everything you do in the app contributes to your **Experience Points**.
- **Leveling Up:** You level up every **100 XP**.
- **Progress:** You can track your level progress on your **Profile Screen** via a real-time progress bar.
- **Celebration:** Reaching a new level triggers a **Level Up! 🎉** celebration dialog.

### 2. Daily Login Streaks 🔥
Consistency is key! 
- **Streak Tracker:** Logging in every day increases your streak count.
- **Streak Bonus:** Every day you maintain your streak, you earn a bonus of `10 XP * Streak Days`.
- **Milestones:** Long-term streaks unlock exclusive badges like the **"7-Day Streak"** or **"Monthly Habit"**.
- **Visuals:** A fire emoji (🔥) and streak count appear in the Leaderboard and as a welcome toast.

### 3. Leaderboards & Hall of Fame 🏆
Compare your progress with the community!
- **Hall of Fame:** A horizontal carousel at the bottom of the **Dashboard** shows the top 5 global users.
- **Global Leaderboard:** Access the full **Top 20** rankings via the Side Menu. Top 3 performers receive Gold, Silver, and Bronze trophies!

---

## 💎 How to Earn XP

| Action | XP Awarded | Recipient |
| :--- | :--- | :--- |
| **Daily Login** | 10 XP + Streak Bonus | All Users |
| **Student Check-in** | 10 XP | Student |
| **Exam Finished** | 50 XP | Student |
| **Proctor Check-in** | 5 XP | Proctor |
| **Exam Dropped Off** | 10 XP | Proctor |
| **Session Completed** | 30 XP (Proctor) / 20 XP (Faculty) | Proctor & Faculty |
| **Report Incident** | 15 XP | Proctor / Admin |
| **Set Availability** | 5 XP | Proctor |
| **Approve Account** | 10 XP | Admin |
| **Create Session** | 15 XP | Admin |
| **Assign Proctor** | 5 XP | Admin |
| **Book a Session** | 10 XP | Admin |
| **Level Milestone** | Bonus Badges | All Users |

> **Note:** Every XP earned also gives the same amount of spendable **Points** for the Shop. XP is permanent (determines your level & rank), while Points can be spent on themes, titles, and sounds.

---

## 🏅 Badge Collection
Badges are displayed on your **Profile Page**. Current badges include:
- **Level Badges:** Automatically earned for every level reached (e.g., "Level 2", "Level 5").
- **7-Day Streak:** Earned by logging in for 7 consecutive days.
- **Monthly Habit:** Earned by maintaining a 30-day login streak.
- **Exam Master:** (Upcoming) Awarded for completing 10 successful deferrals.

---

## 🛠 Technical Implementation (For Developers)

- **Storage:** All gamification data is stored in the `UserAccounts` collection in Firestore.
- **Transactions:** XP awards use Firestore Transactions (`UserMembersClass.awardExperience`) to ensure data integrity during concurrent updates.
- **State Management:** The system uses `MutableStateFlow` in the `DashboardViewModel` to keep the Hall of Fame updated.
- **UI:** Built entirely with **Jetpack Compose**, utilizing `animateFloatAsState` for smooth XP bar transitions and `AnimatedVisibility` for celebratory UI elements.

---

*Happy Deferring!* 🦆