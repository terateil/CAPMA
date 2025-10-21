# CAPMA - Context-Aware Personalized Mobile Agent

This repository contains the source code of our android application.

This Android application demonstrates context-aware perception using multiple sensing modalities:

1. **Activity Recognition** - Detects user activities (walking, running, etc.)
2. **Places API** - Determines the user's location context
3. **Audio Recording** - Records ambient audio and processes it using OpenAI's Whisper API

## Setup Instructions

### Required API Keys

This application requires two API keys:

1. **Google Places API Key**
   - Create a project in the [Google Cloud Console](https://console.cloud.google.com/)
   - Enable the Places API
   - Create an API key with Places API access
   - Add the key to `app/src/main/res/values/strings.xml` in the `google_places_api_key` string resource

2. **OpenAI API Key**
   - Create an account on [OpenAI](https://openai.com/)
   - Generate an API key from your account dashboard
   - Add the key to `app/src/main/res/values/strings.xml` in the `openai_api_key` string resource

### Example strings.xml

```xml
<resources>
    <string name="app_name">CAPMA</string>
    <string name="google_places_api_key">YOUR_GOOGLE_PLACES_API_KEY</string>
    <string name="openai_api_key">YOUR_OPENAI_API_KEY</string>
</resources>
```

## Usage

1. Launch the app and grant all requested permissions
2. Press the "Start Sensing" button
3. The app will collect activity and location data, then begin recording audio
4. Speak while recording
5. Press the button again to stop recording
6. The app will process the audio using OpenAI's Whisper API and display all collected data

## Features

- **Activity Recognition**: Uses Google's Activity Recognition API to detect user activities
- **Places API**: Uses Google Places API to determine location context
- **Audio Recording**: Records ambient audio and measures amplitude
- **Speech-to-Text**: Processes recorded audio using OpenAI's Whisper API

## Implementation Details

The app is structured into modular components:

- `MainActivity`: Main UI and coordination
- `ActivityRecognitionManager`: Handles activity detection
- `PlacesManager`: Handles location context
- `AudioRecorder`: Handles audio recording and amplitude measurement

- `WhisperApiClient`: Handles API communication with OpenAI's Whisper service 
