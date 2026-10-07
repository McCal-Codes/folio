package com.mccal.folio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/**
 * The root hinge helper (ADR 0012). Folio starts it as root with {@code CLASSPATH=<its own APK> app_process /system/bin
 * com.mccal.folio.RootHingeHelper <seconds>}, and it runs nothing else: it listens to Samsung's Folding Angle sensor and
 * prints one line per message to the pipe of the process that started it (see RootHingeProtocol):
 * {@code R} when the sensor is registered, {@code H <nanos> <degrees>} for each reading, {@code E <word>} when it stops.
 *
 * It ends when its input closes (the app died), when it cannot write, or after the given number of seconds, so it never outlives
 * Folio. Java and framework classes only, so it starts fast and has nothing to load but this file.
 */
public final class RootHingeHelper {
    /** Samsung's continuous folding angle. Needs com.samsung.permission.SSENSOR, which root has and the shell user does not. */
    static final int FOLDING_ANGLE = 65686;
    static final int MAX_SECONDS = 300;

    private RootHingeHelper() {}

    /** One reading as the app expects it. Public so the format is tested without a phone. */
    public static String reading(long nanos, float degrees) {
        return String.format(Locale.US, "H %d %.2f", nanos, degrees);
    }

    /** The lifetime asked for, kept between 1 and {@link #MAX_SECONDS}; anything unreadable is the shortest. */
    public static int lifetimeSeconds(String[] args) {
        try {
            int asked = Integer.parseInt(args[0].trim());
            return Math.max(1, Math.min(MAX_SECONDS, asked));
        } catch (Exception e) {
            return 1;
        }
    }

    public static void main(String[] args) {
        final PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false);
        try {
            run(out, lifetimeSeconds(args));
        } catch (Throwable t) {
            say(out, "E died");
        }
        out.flush();
        System.exit(0);
    }

    private static boolean say(PrintStream out, String line) {
        out.print(line);
        out.print('\n');
        out.flush();
        return !out.checkError();
    }

    private static void run(final PrintStream out, int seconds) throws Exception {
        Looper.prepare();
        final Looper looper = Looper.myLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor folding = null;
        List<Sensor> all = manager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : all) {
            if (s.getType() == FOLDING_ANGLE) { folding = s; break; }
        }
        if (folding == null) { say(out, "E no-sensor"); return; }

        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                if (!say(out, reading(event.timestamp, event.values[0]))) looper.quit();
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        if (!manager.registerListener(listener, folding, SensorManager.SENSOR_DELAY_GAME)) { say(out, "E denied"); return; }
        if (!say(out, "R")) return;

        // The app closes our input when it is done or gone: that is the signal to stop.
        Thread watcher = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    InputStream in = System.in;
                    while (in.read() != -1) { /* anything the app sends is ignored */ }
                } catch (Exception ignored) {
                }
                looper.quit();
            }
        });
        watcher.setDaemon(true);
        watcher.start();
        new Handler(looper).postDelayed(new Runnable() {
            @Override public void run() { looper.quit(); }
        }, seconds * 1000L);
        Looper.loop();
        manager.unregisterListener(listener);
    }
}
