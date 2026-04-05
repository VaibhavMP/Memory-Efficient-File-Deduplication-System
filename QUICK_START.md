# Quick Start Guide - Memory Efficient File Deduplication System

## 🚀 Get Started in 3 Steps

### Step 1: Install Java & Maven (One Time Only)
```
Double-click on "1-INSTALL.bat"
```
Wait for "Installation complete!" message, then close the window.

**IMPORTANT**: Close ALL Command Prompt windows and open a NEW one.

### Step 2: Start the Application
```
Double-click on "setup.bat"
```
Or in Command Prompt:
```cmd
cd "c:\Users\mohan\OneDrive\Documents\Desktop\vaibhav\Memory Efficient File Deduplication System"
mvn spring-boot:run
```

Wait for: "Started DeduplicationApplication in X.XXX seconds"

### Step 3: Open Web Browser
Open Chrome or Edge and go to:
```
http://localhost:8080
```

---

## 📝 First Time Setup

### Register Your Account
1. Click "Register" tab
2. Fill in:
   - Username: `myuser`
   - Email: `myuser@email.com`
   - Full Name: `My User`
   - Password: `mypassword`
3. Click "Register" button

### Login
1. Click "Login" tab
2. Enter your username and password
3. Click "Login"

---

## 🎯 Test the Deduplication

### Upload Test Files
1. Drag and drop a file onto the upload area
2. Or click to browse and select a file
3. Watch the deduplication in action!

### See the Magic
- **First upload**: File is stored normally
- **Second upload of same file**: Shows "Exact Duplicate" with 100% savings!
- **Similar files**: Shows similarity percentage

### Check Statistics
- Dashboard shows total files, chunks, storage used
- Deduplication ratio shows space saved
- Real-time updates as you upload

---

## 🔧 Troubleshooting

### "mvn is not recognized"
- You need to close Command Prompt and open a NEW one after running 1-INSTALL.bat

### "java is not recognized"
- Same as above - close and reopen Command Prompt

### Application won't start
- Check if port 8080 is being used by another program
- Try: `netstat -ano | findstr :8080`

### Can't access http://localhost:8080
- Make sure the application is running (you should see "Started DeduplicationApplication")
- Try: http://127.0.0.1:8080

---

## 📊 What You Can Do

### File Operations
- ✅ Upload files (drag & drop or click)
- ✅ View all your files
- ✅ Download files
- ✅ Delete files
- ✅ See file details

### Statistics
- ✅ Total files uploaded
- ✅ Unique chunks stored
- ✅ Storage used vs. saved
- ✅ Deduplication ratio

### Cloud Sync
- ✅ Sync files to cloud (simulated)
- ✅ Check sync status
- ✅ View storage breakdown

### Similarity Detection
- ✅ Find similar files
- ✅ See similarity scores
- ✅ Analyze file content

---

## 🎓 How It Works

### Deduplication Process
1. **File Upload**: You upload a file
2. **Hashing**: System computes SHA-256 hash
3. **Bloom Filter Check**: Fast O(1) duplicate detection
4. **Chunking**: Rabin fingerprinting splits file into chunks
5. **Chunk Deduplication**: Each chunk is checked for duplicates
6. **Storage**: Only unique chunks are stored
7. **Reference**: Duplicate chunks just add a reference

### Example
```
Upload: document_v1.docx (10 MB)
- Stored: 10 MB

Upload: document_v2.docx (10 MB, 99% similar)
- Stored: 0.1 MB (only new chunks)
- Saved: 9.9 MB (99% deduplication!)

Upload: document_v1.docx again (exact duplicate)
- Stored: 0 MB (just a reference)
- Saved: 10 MB (100% deduplication!)
```

---

## 📁 Project Structure

```
Memory Efficient File Deduplication System/
├── src/
│   └── main/
│       ├── java/com/dedup/deduplication/
│       │   ├── DeduplicationApplication.java    # Main app
│       │   ├── config/                          # Configuration
│       │   ├── controller/                      # REST API
│       │   ├── model/                           # Data models
│       │   ├── repository/                      # Storage
│       │   ├── security/                        # JWT auth
│       │   ├── service/                         # Business logic
│       │   └── util/                            # Utilities
│       └── resources/
│           ├── application.properties           # Config
│           └── static/
│               └── index.html                   # Web UI
├── pom.xml                                      # Maven config
├── 1-INSTALL.bat                                # Install Java/Maven
├── setup.bat                                    # Start app
└── README.md                                    # Documentation
```

---

## 🎉 Enjoy!

Your Memory Efficient File Deduplication System is ready to use!

Upload files, watch the deduplication in action, and see how much storage you save!
