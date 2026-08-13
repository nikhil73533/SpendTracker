# SpendTracker

SpendTracker is a production-ready Android application designed to help users efficiently track their income, expenses, and account transactions. It features automated SMS parsing, advanced data visualization, and comprehensive financial reports.

## Features

### 📊 Advanced Charts & Insights
* **Pie Charts**: Visual breakdown of expenses and income by category. Labels are optimized for readability, hiding 0% entries and preventing overlap.
* **Trend Analysis**: Historical context with monthly, weekly, and yearly granularities. Displays current and previous two periods for trend comparison.
* **Dynamic Filtering**: Instant updates when switching between transaction types (Income/Expense) or date ranges.

### 📅 Intuitive Navigation
* **Monthly/Daily Dashboard**: Seamlessly navigate through months and days using interactive sliders and calendars.
* **Week-to-Week Tracking**: Weekly summaries with actual date ranges (e.g., "03 Aug – 09 Aug").

### 📁 Integrated Category Management
* **On-the-fly CRUD**: Create, edit, and delete categories directly within the transaction creation form.
* **Smart Categorization**: ML-powered suggestions for uncategorized transactions.

### 💬 Account & Chat History
* **Unique Sender/Receiver List**: Consolidated list of contacts based on UPI ID or name, sorted by most recent activity.
* **Chat-Style Transaction History**: View your financial interactions with specific entities in a familiar chat interface.
* **Financial Summaries**: Total debit/credit summaries for each account.

### 📥 Data Portability
* **Excel Export**: Export your entire transaction history to a professional `.xlsx` format for external analysis or record-keeping.

## Technical Details
* **Architecture**: Modern Android MVVM pattern with Hilt Dependency Injection.
* **Database**: Local persistence using Room Database.
* **UI Toolkit**: Material Design 3 with custom XML layouts and Jetpack Navigation.
* **Charts**: MPAndroidChart for high-performance data visualization.
* **Data Processing**: Apache POI for Excel generation and custom regex-based SMS parsing.

## Getting Started
1. Clone the repository:
   ```bash
   git clone https://github.com/nikhil73533/SpendTracker.git
   ```
2. Open the project in **Android Studio**.
3. Build and run the app on an emulator or physical device.
4. Grant SMS permissions to enable automated transaction detection.

## License
MIT License - See the [LICENSE](LICENSE) file for details.
