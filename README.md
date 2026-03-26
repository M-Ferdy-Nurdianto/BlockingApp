# Dance Blocking Planner 🕺🟢

A modern, professional Android application for dance choreographers to plan, visualize, and sync stage formations.

## ✨ Features

- **🟢 Green Theme**: A fresh, high-contrast Material 3 interface optimized for stage lighting.
- **⏱️ Timeline Sync**: Pin formations to specific music timestamps with smooth, interpolated transitions.
- **🖼️ Manual Mode**: No music? No problem. Use the frame-by-frame navigation to step through your choreography at 500ms intervals.
- **📐 Real-world Scale**: Set your stage dimensions (e.g., 10m x 10m) and use the meter-grid for pixel-perfect dancer placement.
- **👯 Symmetry Tools**: 
  - **Mirror All**: Instant horizontal flip of the entire formation.
  - **Duplicate**: Copy the previous formation's positions to the current frame.
  - **Sync Position**: Quickly copy one dancer's (X, Y) to another selected dancer.
- **🎥 Export**: Share your formations as high-res images or export the entire sequence as an MP4 video.

## 🚀 Getting Started

1. **Launch App**: The app starts with a default "New Project".
2. **Add Dancers**: Use the green FAB to add dancers to the center stage.
3. **Set Formations**: 
   - Position your dancers.
   - Tap the **Pin** icon to save the formation to the current time.
4. **Playback**: 
   - Load music via the Music icon.
   - Press **Play** to see the dancers move smoothly between your pinned formations.
5. **Detailed Editing**: 
   - Tap a dancer to select them.
   - Tap the **Edit** icon to change their name, color, or sync their position.

## 🛠️ Built With

- **Kotlin** & **Jetpack Compose**
- **MediaCodec** & **MediaMuxer** for high-performance video export.
- **Material 3** components for a premium look.

---
Developed with ❤️ for the dance community.
