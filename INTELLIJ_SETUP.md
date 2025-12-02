# IntelliJ Setup Instructions

## Option 1: Run Configuration Using Shadow JAR (Recommended)

1. **Build the shadow JAR first:**
   - In IntelliJ, open the Gradle tool window (View → Tool Windows → Gradle)
   - Expand `word-of-the-day-generated` → `Tasks` → `other`
   - Double-click `shadowJar` to build it
   - Wait for it to complete

2. **Create Run Configuration:**
   - Run → Edit Configurations...
   - Click "+" → Application
   - Configure:
     - **Name:** `WordOfTheDayPluginTest`
     - **Main class:** `com.wordoftheday.WordOfTheDayPluginTest`
     - **Use classpath of module:** Leave this as "word-of-the-day-generated.main" OR
     - **Alternative:** Click "Modify options" → "Add VM options" → Add: `-ea`
     - **Working directory:** `C:\Users\mruth\word-of-the-day-generated`
     - **VM options:** `-ea -cp "build/libs/word-of-the-day-1.0-SNAPSHOT-all.jar"`
   - Click OK

3. **Run it:**
   - Select the configuration and click Run

## Option 2: Use Gradle Run Task

1. **Open Gradle tool window:**
   - View → Tool Windows → Gradle

2. **Run the task:**
   - Expand `word-of-the-day-generated` → `Tasks` → `other`
   - Double-click `run`
   - This will build and launch RuneLite automatically

## Option 3: Fix Module Classpath

If the module isn't working:

1. **File → Project Structure (Ctrl+Alt+Shift+S)**
2. Go to **Modules**
3. Select `word-of-the-day-generated`
4. Go to **Dependencies** tab
5. Make sure all dependencies are there
6. Click **Apply** and **OK**
7. **File → Invalidate Caches / Restart** → Invalidate and Restart

Then try the run configuration again.

