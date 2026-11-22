package com.ironvellum.app.data;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Runs under the test APK's own UID and reports only the synthetic attachment. */
public final class AiShareTestRecipient extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent send = getIntent();
        ResultReceiver reply = send.getParcelableExtra("test_reply");
        Bundle result = new Bundle();
        result.putInt("uid", Process.myUid());
        Uri uri = send.getParcelableExtra(Intent.EXTRA_STREAM);
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream contents = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int count; (count = input.read(buffer)) != -1;) {
                if (contents.size() + count > 65536) throw new IllegalStateException("Expected a small test fixture");
                contents.write(buffer, 0, count);
            }
            result.putByteArray("contents", contents.toByteArray());
        } catch (Exception e) {
            result.putString("failure", e.toString());
        }
        if (reply != null) reply.send(0, result);
        finish();
    }
}
