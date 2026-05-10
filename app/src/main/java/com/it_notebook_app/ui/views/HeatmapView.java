package com.it_notebook_app.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GitHub-style contribution heatmap.
 *
 * Hiển thị 53 × 7 ô vuông đại diện cho 365 ngày gần nhất.
 * Màu sắc từ xanh nhạt → đậm theo số lượt hoạt động mỗi ngày.
 *
 * Cách dùng:
 *   heatmapView.setActivityData(map);   // map: "yyyyMMdd" → count
 */
public class HeatmapView extends View {

    // ─── Màu 5 mức (0 = không hoạt động, 4 = nhiều nhất) ────────────────────
    private static final int[] LEVEL_COLORS = {
            0xFF1A1A2E,   // level 0 — không hoạt động
            0xFF0D4A2F,   // level 1 — 1–2 lượt
            0xFF166534,   // level 2 — 3–5 lượt
            0xFF16A34A,   // level 3 — 6–9 lượt
            0xFF4ADE80,   // level 4 — 10+ lượt
    };

    private static final int COLS = 53; // 53 tuần
    private static final int ROWS = 7;  // 7 ngày / tuần

    private final Paint  cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF  cellRect  = new RectF();
    private final Calendar calendar = Calendar.getInstance(); // tái dùng, tránh tạo mới mỗi frame

    private Map<String, Integer> activityData = new HashMap<>();
    private long startEpochDay; // ngày bắt đầu (units: ngày kể từ epoch)

    // ─── Constructors ─────────────────────────────────────────────────────────

    public HeatmapView(Context context) {
        super(context);
        init();
    }

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeatmapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        long todayEpochDay = System.currentTimeMillis() / 86_400_000L;
        startEpochDay = todayEpochDay - (long) (COLS * ROWS - 1);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Cập nhật dữ liệu và vẽ lại biểu đồ.
     *
     * @param data Map từ "yyyyMMdd" → số lượt hoạt động trong ngày đó
     */
    public void setActivityData(Map<String, Integer> data) {
        activityData = (data != null) ? data : new HashMap<>();
        invalidate();
    }

    // ─── Measure & Draw ───────────────────────────────────────────────────────

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width    = MeasureSpec.getSize(widthMeasureSpec);
        float cell   = cellSize(width);
        float gap    = cellGap(cell);
        int height   = (int) (ROWS * cell + (ROWS - 1) * gap
                + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int   width  = getWidth() - getPaddingLeft() - getPaddingRight();
        float cell   = cellSize(width);
        float gap    = cellGap(cell);
        float radius = cell * 0.25f;

        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                long   epochDay = startEpochDay + (long) col * ROWS + row;
                String key      = epochDayToKey(epochDay);
                int    count    = activityData.containsKey(key) ? activityData.get(key) : 0;

                float left  = getPaddingLeft() + col * (cell + gap);
                float top   = getPaddingTop()  + row * (cell + gap);

                cellRect.set(left, top, left + cell, top + cell);
                cellPaint.setColor(LEVEL_COLORS[countToLevel(count)]);
                canvas.drawRoundRect(cellRect, radius, radius, cellPaint);
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private float cellSize(int totalWidth) {
        return (totalWidth - (COLS - 1) * 2f) / COLS;
    }

    private float cellGap(float cellSize) {
        return Math.max(1.5f, cellSize * 0.15f);
    }

    private int countToLevel(int count) {
        if (count == 0)  return 0;
        if (count <= 2)  return 1;
        if (count <= 5)  return 2;
        if (count <= 9)  return 3;
        return 4;
    }

    /** Chuyển epoch day (đơn vị ngày kể từ 1970-01-01) thành key "yyyyMMdd". */
    private String epochDayToKey(long epochDay) {
        calendar.setTimeInMillis(epochDay * 86_400_000L);
        return String.format(Locale.US, "%04d%02d%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }
}
