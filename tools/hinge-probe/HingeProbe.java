import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Looper;
import java.util.*;

/** Runs as the shell user (app_process). Read-only: lists the fold sensors and listens for a few seconds. */
public class HingeProbe {
    static final Map<Integer, TreeSet<Float>> seen = new LinkedHashMap<>();
    static final Map<Integer, Integer> count = new LinkedHashMap<>();
    // Each change of value with the second it arrived, so a reading can be matched to what the phone was doing.
    static final Map<Integer, java.util.List<String>> timeline = new LinkedHashMap<>();
    static final Map<Integer, Float> last = new LinkedHashMap<>();
    static long startedAt;

    public static void main(String[] args) throws Exception {
        startedAt = System.nanoTime();
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 6;
        Looper.prepare();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context ctx = (Context) at.getMethod("getSystemContext").invoke(thread);
        System.out.println("uid=" + android.os.Process.myUid() + " opPackage=" + ctx.getOpPackageName());
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        System.out.println("sensors visible to shell: " + all.size());
        final int[] wanted = {36, 65686, 65695};
        for (Sensor s : all) {
            for (int w : wanted) if (s.getType() == w) System.out.println("  visible: type " + s.getType() + " " + s.getName() + " " + s.getStringType());
        }
        SensorEventListener l = new SensorEventListener() {
            public void onSensorChanged(SensorEvent e) {
                int t = e.sensor.getType();
                seen.computeIfAbsent(t, k -> new TreeSet<>()).add(e.values[0]);
                Float before = last.put(t, e.values[0]);
                if (before == null || before != e.values[0]) timeline.computeIfAbsent(t, k -> new java.util.ArrayList<>())
                    .add(String.format(java.util.Locale.US, "%.1fs=%s", (System.nanoTime() - startedAt) / 1e9, e.values[0]));
                count.merge(t, 1, Integer::sum);
            }
            public void onAccuracyChanged(Sensor s, int a) {}
        };
        for (int w : wanted) {
            Sensor s = null;
            for (Sensor x : all) if (x.getType() == w) { s = x; break; }
            if (s == null) { System.out.println("type " + w + ": not in the list the shell can see"); continue; }
            try {
                boolean ok = sm.registerListener(l, s, SensorManager.SENSOR_DELAY_GAME);
                System.out.println("type " + w + ": registerListener -> " + ok);
            } catch (Throwable t) {
                System.out.println("type " + w + ": registerListener threw " + t);
            }
        }
        final Looper main = Looper.myLooper();
        new android.os.Handler(main).postDelayed(main::quit, seconds * 1000L);
        Looper.loop();
        for (int w : wanted) {
            TreeSet<Float> v = seen.get(w);
            System.out.println("type " + w + ": " + count.getOrDefault(w, 0) + " events, " + (v == null ? 0 : v.size()) + " distinct values"
                + (v == null || v.isEmpty() ? "" : " min=" + v.first() + " max=" + v.last()));
            java.util.List<String> tl = timeline.get(w);
            if (tl != null) System.out.println("  changes: " + (tl.size() > 70 ? tl.subList(0, 35) + " ... " + tl.subList(tl.size() - 35, tl.size()) : tl));
        }
        System.exit(0);
    }
}
