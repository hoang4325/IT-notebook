package com.it_notebook_app.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.it_notebook_app.data.db.AppDatabase;
import com.it_notebook_app.data.db.FormulaDao;
import com.it_notebook_app.data.model.Formula;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FormulaRepository {

    private final FormulaDao     formulaDao;
    private final ExecutorService executor;

    public FormulaRepository(Application application) {
        formulaDao = AppDatabase.getInstance(application).formulaDao();
        executor   = Executors.newSingleThreadExecutor();
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<List<Formula>> getAll()                             { return formulaDao.getAll(); }
    public LiveData<List<Formula>> getAllByTopic(int topicId)           { return formulaDao.getAllByTopic(topicId); }
    public LiveData<List<Formula>> getAllByTopicRecent(int topicId)     { return formulaDao.getAllByTopicRecent(topicId); }
    public LiveData<List<Formula>> getFavorites()                       { return formulaDao.getFavorites(); }
    public LiveData<List<Formula>> getFavoritesByTopic(int topicId)    { return formulaDao.getFavoritesByTopic(topicId); }
    public LiveData<List<Formula>> getRecentlyViewed()                 { return formulaDao.getRecentlyViewed(); }
    public LiveData<Formula>       getById(int id)                     { return formulaDao.getById(id); }
    public LiveData<List<Formula>> searchByKeyword(String keyword)     { return formulaDao.searchByKeyword(keyword); }

    public LiveData<List<Formula>> searchByKeywordAndTag(String keyword, String tag) {
        return formulaDao.searchByKeywordAndTag(keyword, tag);
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    public void insert(Formula formula)              { executor.execute(() -> formulaDao.insert(formula)); }
    public void update(Formula formula)              { executor.execute(() -> formulaDao.update(formula)); }
    public void delete(Formula formula)              { executor.execute(() -> formulaDao.delete(formula)); }
    public void deleteById(int id)                  { executor.execute(() -> formulaDao.deleteById(id)); }
    public void setFavorite(int id, boolean fav)    { executor.execute(() -> formulaDao.setFavorite(id, fav)); }
    public void clearRecentlyViewed()               { executor.execute(formulaDao::clearRecentlyViewed); }

    public void updateLastViewedAt(int id, long timestamp) {
        executor.execute(() -> formulaDao.updateLastViewedAt(id, timestamp));
    }

    public int getFormulaCountByTopic(int topicId) {
        return formulaDao.getFormulaCountByTopic(topicId);
    }

    // ─── Heatmap ──────────────────────────────────────────────────────────────

    /**
     * Lấy dữ liệu hoạt động 365 ngày gần nhất dưới dạng Map{"yyyyMMdd" → count}.
     * Chạy trên background thread; kết quả trả về qua callback.
     */
    public void getActivityMap(Consumer<Map<String, Integer>> callback) {
        executor.execute(() -> {
            long since = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1_000;
            List<Long> timestamps = formulaDao.getActivitySince(since);

            Map<String, Integer> map = new HashMap<>();
            Calendar cal = Calendar.getInstance();

            for (Long ts : timestamps) {
                if (ts == null || ts == 0) continue;
                cal.setTimeInMillis(ts);
                String key = String.format(Locale.US, "%04d%02d%02d",
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH));
                map.merge(key, 1, Integer::sum); // gọn hơn getOrDefault + put
            }

            callback.accept(map);
        });
    }
}
