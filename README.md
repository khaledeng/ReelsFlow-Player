# 🎬 ReelsFlow

### Transform Your Local Videos Into an Infinite Vertical Feed

ReelsFlow is a modern offline-first Android video player that turns your personal video library into an immersive vertical scrolling experience inspired by TikTok, Instagram Reels, and YouTube Shorts.

Built with Jetpack Compose, Media3 ExoPlayer, Room Database, and Clean Architecture, ReelsFlow delivers smooth playback, intelligent preloading, advanced gesture controls, and complete privacy.

---

## ✨ Features

### 🎥 Infinite Vertical Feed

Browse your local videos through a seamless vertical feed experience.

### ⚡ Instant Playback

Powered by a custom ExoPlayer pooling system with intelligent preloading.

### 🔍 Pinch To Zoom

Zoom up to 4x and freely pan around videos.

### ❤️ Favorites System

Double tap any video to instantly add it to favorites.

### 📂 Folder Filtering

Play videos from specific folders like:

* Camera
* Downloads
* Movies
* WhatsApp
* Custom folders

### 🎚 Smart Gesture Controls

#### Left Edge

Adjust screen brightness.

#### Right Edge

Adjust media volume.

#### Long Press

Enable temporary 2X playback speed.

#### Single Tap

Play / Pause.

#### Double Tap

Favorite video.

### 📐 Multiple Aspect Modes

* Fit
* Fill
* Stretch

### 🗑 Secure Video Deletion

Supports Android Scoped Storage APIs.

### 🔀 Advanced Playback Modes

* Sequential
* Shuffle
* Newest First
* Oldest First

### 🔁 Infinite Loop Navigation

Never reach the end of your playlist.

---

## 🏗 Architecture

ReelsFlow follows Clean Architecture principles.

```text
Presentation Layer
│
├── Jetpack Compose UI
├── ViewModels
└── StateFlow

Domain Layer
│
├── Entities
├── Repository Contracts
└── Business Rules

Data Layer
│
├── MediaStore
├── Room Database
└── Repository Implementations
```

---

## 🚀 Tech Stack

### UI

* Jetpack Compose
* Material 3
* Compose Foundation Pager

### Media

* AndroidX Media3
* ExoPlayer

### Data

* Room Database
* Kotlin Coroutines
* StateFlow

### Architecture

* Clean Architecture
* Repository Pattern
* MVVM

### Language

* Kotlin

---

## ⚙ Performance Optimizations

### ExoPlayer Pooling

Instead of creating a player per video, ReelsFlow maintains a lightweight player pool.

```text
Previous Video
Current Video
Next Video
```

Only three players stay active.

### Background Preloading

Adjacent videos are prepared in advance for instant transitions.

### Lazy Initialization

Heavy resources are initialized only when required.

### Lifecycle Aware Playback

Players automatically pause and release resources when the app moves to the background.

### Compose State Isolation

Real-time playback progress updates do not trigger full-screen recompositions.

---

## 📱 Screenshots

| Feed                 | Gestures             | Zoom                 |
| -------------------- | -------------------- | -------------------- |
| Add screenshots here | Add screenshots here | Add screenshots here |

---

## 🔐 Privacy First

ReelsFlow is completely offline.

* No account required
* No analytics tracking
* No cloud storage
* No external servers

Your videos stay on your device.

---

## 🎯 Why ReelsFlow?

Traditional video players force users into folders, lists, and menus.

ReelsFlow reimagines local video playback with:

* Modern vertical feed UX
* Instant navigation
* Smart gestures
* Full privacy
* High-performance playback

Think:

> TikTok for your offline videos.

---

## 📦 Installation

Clone the repository:

```bash
git clone https://github.com/yourusername/ReelsFlow.git
```

Open with Android Studio and run:

```bash
./gradlew installDebug
```

---

## 🛣 Roadmap

* [ ] Move videos between folders
* [ ] Multi-select actions
* [ ] Batch delete
* [ ] Search system
* [ ] Recently watched feed
* [ ] Smart recommendations
* [ ] Recycle bin
* [ ] Statistics dashboard

---

## 🤝 Contributing

Contributions are welcome.

Feel free to open issues, suggest features, or submit pull requests.

---

## 📄 License

MIT License

---

### Built with ❤️ using Kotlin, Jetpack Compose, and Media3 ExoPlayer.
