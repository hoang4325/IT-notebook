package com.it_notebook_app.ui.formula;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.it_notebook_app.R;
import com.it_notebook_app.data.model.Formula;
import com.it_notebook_app.data.remote.GeminiService;
import com.it_notebook_app.databinding.FragmentFormulaDetailBinding;

import io.noties.markwon.Markwon;

public class FormulaDetailFragment extends Fragment {

    private FragmentFormulaDetailBinding binding;
    private FormulaDetailViewModel       viewModel;
    private Formula                      currentFormula;
    private Markwon                      markwon;
    private int                          formulaId = -1;
    private String                       currentInterviewQuestions = "";

    private static final String[] TRANSLATE_LANGUAGES = {
            "Python", "JavaScript", "TypeScript", "C++", "C#",
            "Go", "Rust", "Kotlin", "Swift", "PHP"
    };

    public FormulaDetailFragment() {
        super(R.layout.fragment_formula_detail);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding   = FragmentFormulaDetailBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(FormulaDetailViewModel.class);
        markwon   = Markwon.create(requireContext());

        if (getArguments() != null) {
            formulaId = getArguments().getInt("formulaId", -1);
        }

        if (formulaId != -1) {
            viewModel.updateLastViewed(formulaId);
            observeFormula();
        }

        setupActions();
        observeAiResult();
        observeTranslateResult();
        observeInterviewResult();
        observeGradeResult();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─── Data binding ─────────────────────────────────────────────────────────

    private void observeFormula() {
        viewModel.getFormula(formulaId).observe(getViewLifecycleOwner(), formula -> {
            if (formula == null) return;
            currentFormula = formula;
            bindFormula(formula);
        });
    }

    private void bindFormula(Formula formula) {
        binding.tvTitle.setText(formula.getTitle());
        binding.tvContent.setText(formula.getContent());
        binding.tvExplanation.setText(formula.getExplanation());
        updateFavoriteIcon(formula.isFavorite());

        // Examples — hide section khi không có dữ liệu
        boolean hasExamples = formula.getExamples() != null && !formula.getExamples().isEmpty();
        binding.tvExamples.setVisibility(hasExamples ? View.VISIBLE : View.GONE);
        binding.tvExamplesLabel.setVisibility(hasExamples ? View.VISIBLE : View.GONE);
        if (hasExamples) binding.tvExamples.setText(formula.getExamples());

        // Tags chips
        binding.chipGroupTags.removeAllViews();
        if (formula.getTags() != null) {
            for (String tag : formula.getTags()) {
                Chip chip = new Chip(requireContext());
                chip.setText("#" + tag);
                chip.setTextColor(0xFF9AA0C4);
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setChipStrokeColorResource(android.R.color.darker_gray);
                chip.setChipStrokeWidth(1f);
                chip.setClickable(false);
                binding.chipGroupTags.addView(chip);
            }
        }
    }

    private void updateFavoriteIcon(boolean isFavorite) {
        binding.btnFavorite.setImageResource(
                isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        binding.btnFavorite.setColorFilter(isFavorite ? 0xFFFFD700 : 0xFF9AA0C4);
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void setupActions() {
        binding.btnBack.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigateUp());

        binding.btnFavorite.setOnClickListener(v -> {
            if (currentFormula != null) viewModel.toggleFavorite(currentFormula);
        });

        binding.btnCopy.setOnClickListener(v -> {
            if (currentFormula != null && currentFormula.getContent() != null) {
                copyToClipboard("formula", currentFormula.getContent());
                showSnack("✓ Copied to clipboard");
            }
        });

        binding.btnEdit.setOnClickListener(v -> {
            if (currentFormula == null) return;
            Bundle bundle = new Bundle();
            bundle.putInt("formulaId", currentFormula.getId());
            bundle.putInt("topicId", currentFormula.getTopicId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_formulaDetail_to_addEditFormula, bundle);
        });

        binding.btnDelete.setOnClickListener(v -> {
            if (currentFormula == null) return;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa công thức")
                    .setMessage("Bạn có chắc muốn xóa \"" + currentFormula.getTitle()
                            + "\"? Hành động này không thể hoàn tác.")
                    .setPositiveButton("Xóa", (d, w) -> {
                        viewModel.deleteFormula(currentFormula);
                        Navigation.findNavController(requireView()).navigateUp();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // AI Explain
        binding.btnAiExplain.setOnClickListener(v -> {
            if (!guardHasCode()) return;
            binding.cardAiResult.setVisibility(View.VISIBLE);
            viewModel.explainWithAi(currentFormula);
        });

        binding.btnSaveAiExplanation.setOnClickListener(v -> {
            GeminiService.AiResult latest = viewModel.getAiResult().getValue();
            if (currentFormula == null || latest == null
                    || latest.status != GeminiService.AiResult.Status.SUCCESS) return;
            viewModel.saveAiExplanation(currentFormula, latest.text);
            binding.btnSaveAiExplanation.setVisibility(View.GONE);
            showSnack("✓ Đã lưu giải thích AI vào ghi chú");
        });

        // Translate
        binding.btnTranslateCode.setOnClickListener(v -> {
            if (!guardHasCode()) return;
            String code = currentFormula.getContent();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("⇄ Dịch code sang ngôn ngữ nào?")
                    .setItems(TRANSLATE_LANGUAGES, (d, which) -> {
                        String target = TRANSLATE_LANGUAGES[which];
                        binding.tvTranslateHeader.setText("⇄ Dịch sang " + target);
                        binding.tvTranslateLoadingText.setText("Đang dịch sang " + target + "...");
                        binding.cardTranslateResult.setVisibility(View.VISIBLE);
                        viewModel.translateWithAi(code, target);
                    })
                    .show();
        });

        binding.btnCopyTranslated.setOnClickListener(v -> {
            GeminiService.AiResult latest = viewModel.getTranslateResult().getValue();
            if (latest != null && latest.text != null) {
                copyToClipboard("translated_code", latest.text);
                showSnack("✓ Đã copy code đã dịch");
            }
        });

        // Interview
        binding.btnInterview.setOnClickListener(v -> {
            if (!guardHasCode()) return;
            resetInterviewUi();
            viewModel.generateInterviewQuestions(
                    currentFormula.getTitle(), currentFormula.getContent());
        });

        binding.btnSubmitAnswer.setOnClickListener(v -> {
            String answer = binding.etUserAnswer.getText().toString().trim();
            if (answer.isEmpty()) { showSnack("Hãy nhập câu trả lời trước!"); return; }
            if (currentFormula == null || currentInterviewQuestions.isEmpty()) return;

            binding.tvGradeResult.setVisibility(View.GONE);
            binding.tvInterviewPhase.setText("Đang chấm điểm...");
            binding.btnSubmitAnswer.setEnabled(false);
            viewModel.gradeAnswer(currentFormula.getContent(), currentInterviewQuestions, answer);
        });
    }

    // ─── AI Observers ─────────────────────────────────────────────────────────

    private void observeAiResult() {
        viewModel.getAiResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            binding.cardAiResult.setVisibility(View.VISIBLE);

            boolean loading   = result.status == GeminiService.AiResult.Status.LOADING;
            boolean hasText   = result.status == GeminiService.AiResult.Status.STREAMING
                             || result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isSuccess = result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isError   = result.status == GeminiService.AiResult.Status.ERROR;

            binding.layoutAiLoading.setVisibility(loading  ? View.VISIBLE : View.GONE);
            binding.tvAiResult.setVisibility(hasText        ? View.VISIBLE : View.GONE);
            binding.tvAiError.setVisibility(isError         ? View.VISIBLE : View.GONE);

            if (hasText)   markwon.setMarkdown(binding.tvAiResult, result.text);
            if (isError)   binding.tvAiError.setText("⚠ " + result.error);

            boolean alreadySaved = isSuccess && currentFormula != null
                    && result.text.equals(currentFormula.getExplanation());
            binding.btnSaveAiExplanation.setVisibility(
                    isSuccess && !alreadySaved ? View.VISIBLE : View.GONE);
        });
    }

    private void observeTranslateResult() {
        viewModel.getTranslateResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            binding.cardTranslateResult.setVisibility(View.VISIBLE);

            boolean loading   = result.status == GeminiService.AiResult.Status.LOADING;
            boolean streaming = result.status == GeminiService.AiResult.Status.STREAMING;
            boolean isSuccess = result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isError   = result.status == GeminiService.AiResult.Status.ERROR;

            binding.layoutTranslateLoading.setVisibility(loading    ? View.VISIBLE : View.GONE);
            binding.tvTranslatedCode.setVisibility((streaming || isSuccess) ? View.VISIBLE : View.GONE);
            binding.tvTranslateError.setVisibility(isError          ? View.VISIBLE : View.GONE);
            binding.btnCopyTranslated.setVisibility(isSuccess       ? View.VISIBLE : View.GONE);

            if (streaming || isSuccess) binding.tvTranslatedCode.setText(result.text);
            if (isError)   binding.tvTranslateError.setText("⚠ " + result.error);
        });
    }

    private void observeInterviewResult() {
        viewModel.getInterviewResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            binding.cardInterview.setVisibility(View.VISIBLE);

            boolean loading   = result.status == GeminiService.AiResult.Status.LOADING;
            boolean hasText   = result.status == GeminiService.AiResult.Status.STREAMING
                             || result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isSuccess = result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isError   = result.status == GeminiService.AiResult.Status.ERROR;

            binding.layoutInterviewLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.tvInterviewQuestions.setVisibility(hasText   ? View.VISIBLE : View.GONE);
            binding.tvInterviewError.setVisibility(isError       ? View.VISIBLE : View.GONE);

            if (hasText) markwon.setMarkdown(binding.tvInterviewQuestions, result.text);
            if (isError) binding.tvInterviewError.setText("⚠ " + result.error);

            if (isSuccess) {
                currentInterviewQuestions = result.text;
                setAnswerSectionVisible(true);
                binding.tvInterviewPhase.setText("Sẵn sàng trả lời");
            }
        });
    }

    private void observeGradeResult() {
        viewModel.getGradeResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            boolean hasText   = result.status == GeminiService.AiResult.Status.STREAMING
                             || result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isSuccess = result.status == GeminiService.AiResult.Status.SUCCESS;
            boolean isError   = result.status == GeminiService.AiResult.Status.ERROR;

            binding.tvGradeResult.setVisibility(hasText ? View.VISIBLE : View.GONE);
            if (hasText) markwon.setMarkdown(binding.tvGradeResult, result.text);

            if (isError) {
                binding.tvInterviewError.setVisibility(View.VISIBLE);
                binding.tvInterviewError.setText("⚠ Lỗi chấm điểm: " + result.error);
            }
            if (isSuccess || isError) {
                binding.btnSubmitAnswer.setEnabled(true);
                if (isSuccess) binding.tvInterviewPhase.setText("Đã chấm điểm ✓");
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Kiểm tra formula có nội dung code, hiện Snackbar nếu không. */
    private boolean guardHasCode() {
        if (currentFormula == null) return false;
        String code = currentFormula.getContent();
        if (code == null || code.trim().isEmpty()) {
            showSnack("Công thức này chưa có nội dung code");
            return false;
        }
        return true;
    }

    /** Reset toàn bộ Interview card về trạng thái ban đầu (đang loading). */
    private void resetInterviewUi() {
        binding.cardInterview.setVisibility(View.VISIBLE);
        binding.layoutInterviewLoading.setVisibility(View.VISIBLE);
        binding.tvInterviewPhase.setText("Đang tạo câu hỏi...");
        binding.tvInterviewQuestions.setVisibility(View.GONE);
        binding.tvInterviewError.setVisibility(View.GONE);
        binding.tvGradeResult.setVisibility(View.GONE);
        setAnswerSectionVisible(false);
        currentInterviewQuestions = "";
    }

    /** Hiện/ẩn phần nhập câu trả lời (divider, label, input, nút nộp). */
    private void setAnswerSectionVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        binding.dividerAnswer.setVisibility(v);
        binding.tvAnswerLabel.setVisibility(v);
        binding.etUserAnswer.setVisibility(v);
        binding.btnSubmitAnswer.setVisibility(v);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    private void showSnack(String message) {
        Snackbar.make(binding.coordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
    }
}
