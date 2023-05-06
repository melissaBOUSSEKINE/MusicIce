package com.example.musicapp;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FirstFragment extends Fragment {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private String fileName = null;
    private MediaRecorder recorder = null;

    private Button btnRecord;
    private Button btnStop;
    private String RECORD_FILE_PATH = null;
    private boolean isRecording = false;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(getActivity(), "Permission granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
            });

    public FirstFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_first, container, false);

        btnRecord = view.findViewById(R.id.btnRecord);
        btnStop = view.findViewById(R.id.btnStop);

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    startRecording();
                }
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopRecording();
                }
            }
        });

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);

        fileName = getActivity().getExternalCacheDir().getAbsolutePath();
        fileName += "/audio_record_test.3gp";

        return view;
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e("AUDIO_RECORDER", "prepare() failed");
            Toast.makeText(getActivity(), "Recording failed to start", Toast.LENGTH_LONG).show();
            return;
        }

        recorder.start();
        isRecording = true;
        btnRecord.setText("Recording...");
        btnStop.setVisibility(View.VISIBLE);
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            isRecording = false;
            btnRecord.setText("Record");
            btnStop.setVisibility(View.INVISIBLE);

            File recordedFile = new File(fileName);
            if (recordedFile.exists() && recordedFile.length() > 0) {
                Toast.makeText(getActivity(), "Recording saved: " + fileName, Toast.LENGTH_LONG).show();
                Log.d("RECORD_FILE_PATH", "Recording saved: " + fileName);

                RECORD_FILE_PATH = fileName;
                // Upload the recorded file to server
                new UploadTask().executeOnExecutor(Executors.newSingleThreadExecutor());
            }
        }
    }

    /*private class UploadTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {
                OkHttpClient client = new OkHttpClient();
                MediaType mediaType = MediaType.parse("audio/3gpp");
                RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("audio", "audio_record_test.3gp",
                                RequestBody.create(new File(RECORD_FILE_PATH), mediaType))
                        .build();
                Request request = new Request.Builder()
                        .url("http://192.168.43.99:5000/asr")
                        .method("POST", body)
                        .build();
                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // Update UI with the result
            Log.d(TAG, "Result: " + result);
            if (result != null) {
                Toast.makeText(getActivity(), "ASR successful: " + result, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), "ASR failed", Toast.LENGTH_SHORT).show();
            }
        }
    }*/
    private class UploadTask extends AsyncTask<Void, Void, String[]> {
        @Override
        protected String[] doInBackground(Void... voids) {
            try {
                OkHttpClient client = new OkHttpClient();

                // Part 1: ASR Request
                MediaType mediaType = MediaType.parse("audio/3gpp");
                RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("audio", "audio_record_test.3gp",
                                RequestBody.create(new File(RECORD_FILE_PATH), mediaType))
                        .build();
                Request request = new Request.Builder()
                        .url("http://192.168.43.99:5000/asr")
                        .method("POST", body)
                        .build();
                Response response = client.newCall(request).execute();
                String asrResult = response.body().string();

                // Part 2: TAL Request
                MediaType jsonType = MediaType.parse("application/json");
                JSONObject talInput = new JSONObject();
                talInput.put("text", asrResult);
                RequestBody talBody = RequestBody.create(talInput.toString(), jsonType);
                Request talRequest = new Request.Builder()
                        .url("http://192.168.43.99:5000/tal")
                        .method("POST", talBody)
                        .build();
                Response talResponse = client.newCall(talRequest).execute();
                String talResult = talResponse.body().string();

                return new String[] { asrResult, talResult };

            } catch (IOException | JSONException e) {
                Log.e(TAG, "IOException or JSONException: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] results) {
            // Update UI with the results
            Log.d(TAG, "ASR Result: " + results[0]);
            Log.d(TAG, "TAL Result: " + results[1]);

            if (results != null) {
                Toast.makeText(getActivity(), "ASR successful: " + results[0] + "\nTAL successful: " + results[1], Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), "ASR or TAL failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

}



