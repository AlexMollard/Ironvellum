package com.ironvellum.app.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** The separate test APK process serves and grants only its synthetic documents. */
public final class IncomingAiTestSender extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }

    @Override public Bundle call(String authority, String method, String arg, Bundle extras) {
        Context sender = Objects.requireNonNull(getContext());
        String receiver = getCallingPackage();
        if (!"com.ironvellum.app".equals(receiver)) throw new SecurityException("Only the app under test may call");
        Uri uri = new Uri.Builder().scheme("content").authority(authority).appendPath("workout")
            .appendPath(UUID.fromString(arg).toString()).build();
        int read = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        // Use the test sender's own identity to grant and revoke, even during a Binder call.
        long identity = android.os.Binder.clearCallingIdentity();
        try {
            switch (method) {
                case "write":
                    try (FileOutputStream output = new FileOutputStream(file(uri))) {
                        output.write(Objects.requireNonNull(extras.getString("text")).getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    break;
                case "grant":
                    sender.grantUriPermission(receiver, uri, read);
                    break;
                case "revoke":
                    sender.revokeUriPermission(receiver, uri, read);
                    break;
                case "delete":
                    file(uri).delete();
                    break;
                default:
                    throw new IllegalArgumentException(method);
            }
        } finally {
            android.os.Binder.restoreCallingIdentity(identity);
        }
        Bundle result = new Bundle();
        result.putInt("uid", Process.myUid());
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only fixture");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File file(Uri uri) {
        return new File(Objects.requireNonNull(getContext()).getCacheDir(),
            "incoming-ai-" + UUID.fromString(uri.getLastPathSegment()) + ".json");
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        throw new UnsupportedOperationException();
    }
}
