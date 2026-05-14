package com.navioverlay.car.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;

import java.util.List;

final class InfoDialogsHelper {
    private static final int BG = 0xFF030712;
    private static final int BG_2 = 0xFF071021;
    private static final int CARD_2 = 0xFF1A2538;
    private static final int STROKE = 0x4460708A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFC4CBD7;

    private InfoDialogsHelper() {}

    static void showRecentTrackHistoryDialog(Activity activity, Prefs prefs) {
        final Dialog dialog = fullDialog(activity);
        LinearLayout outer = dialogRoot(activity);
        dialog.setContentView(outer);
        outer.addView(tv(activity, tr(prefs, "История последних треков", "Recent track history"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(activity, tr(prefs, "Последние 30 треков. Справа можно быстро скопировать название строки.", "Latest 30 tracks. Use the right-side button to quickly copy the full line."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(activity, outer, 12);

        ScrollView sv = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        List<Prefs.RecentTrack> tracks = prefs.recentTracks();
        if (tracks.isEmpty()) {
            box.addView(tv(activity, tr(prefs, "История пока пустая.", "History is empty for now."), 15, MUTED, Typeface.NORMAL, Gravity.CENTER), btnLp(activity));
        } else {
            int shown = 0;
            for (int i = tracks.size() - 1; i >= 0 && shown < 30; i--, shown++) {
                Prefs.RecentTrack item = tracks.get(i);
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, activity));
                Ui.pad(row, 14, 12, 12, 12);

                TextView text = tv(activity, (shown + 1) + ". " + item.display(), 15, TEXT, Typeface.NORMAL, Gravity.START);
                text.setLineSpacing(Ui.dp(activity, 2), 1.0f);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

                Button copy = secondaryButton(activity, tr(prefs, "Копировать", "Copy"), v -> {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("track", item.display()));
                    Toast.makeText(activity, tr(prefs, "Трек скопирован", "Track copied"), Toast.LENGTH_SHORT).show();
                });
                LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(Ui.dp(activity, 112), Ui.dp(activity, 42));
                copyLp.leftMargin = Ui.dp(activity, 10);
                row.addView(copy, copyLp);
                box.addView(row, smallGapLp(activity));
            }
        }

        outer.addView(secondaryButton(activity, tr(prefs, "Готово", "Done"), v -> dialog.dismiss()), btnLp(activity));
        dialog.show();
        fitDialog(dialog, true);
    }

    static void showInstructionsDialog(Activity activity, Prefs prefs) {
        final Dialog dialog = fullDialog(activity);
        LinearLayout outer = dialogRoot(activity);
        dialog.setContentView(outer);
        outer.addView(tv(activity, tr(prefs, "Описание функций", "Feature descriptions"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(activity, tr(prefs, "Короткое описание всех важных функций приложения простыми словами.", "A short plain-language description of the app’s important functions."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(activity, outer, 12);

        ScrollView sv = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addInstructionItem(activity, box, tr(prefs, "Включить приложение", "Enable app"), tr(prefs, "Полностью включает или отключает работу Navi Overlay.", "Fully enables or disables Navi Overlay."));
        addInstructionItem(activity, box, tr(prefs, "Показывать только поверх выбранных приложений", "Show only over selected apps"), tr(prefs, "Окно будет показываться только поверх выбранных навигаторов.", "The window will appear only over the selected navigation apps."));
        addInstructionItem(activity, box, tr(prefs, "Приложения навигации", "Navigation apps"), tr(prefs, "Список навигаторов, поверх которых может показываться окно.", "List of navigation apps where the window may appear."));
        addInstructionItem(activity, box, tr(prefs, "Аудио приложения", "Audio apps"), tr(prefs, "Список плееров, из которых брать название трека и обложку.", "List of players used for track title and album art."));
        addInstructionItem(activity, box, tr(prefs, "Приложения с приоритетным окном", "Priority overlay apps"), tr(prefs, "Если у этих приложений появляется своё окно, Navi Overlay временно уступает ему место.", "If these apps show their own window, Navi Overlay temporarily gives it priority."));
        addInstructionItem(activity, box, tr(prefs, "Настройки текста", "Text settings"), tr(prefs, "Размер, тень, выравнивание, шрифт и жирность текста в окне.", "Size, shadow, alignment, font and bold style of the text in the window."));
        addInstructionItem(activity, box, tr(prefs, "Настройки окна", "Window settings"), tr(prefs, "Прозрачность, форма, отступы, время показа, положение окна и поведение при паузе.", "Transparency, border shape, offsets, display time, position and pause behavior."));
        addInstructionItem(activity, box, tr(prefs, "Настройки цвета", "Color settings"), tr(prefs, "Меняет цвета окна, рамки, текста, полосы перемотки и других элементов.", "Changes the colors of the window, border, text, seek bar and other elements."));
        addInstructionItem(activity, box, tr(prefs, "Положение окна", "Window position"), tr(prefs, "Переносит окно в один из стандартных углов или в центр экрана.", "Moves the window to one of the standard corners or to the center of the screen."));
        addInstructionItem(activity, box, tr(prefs, "Пресет дизайна", "Design preset"), tr(prefs, "Быстро меняет общий стиль окна: форму, плотность и характер рамки.", "Quickly changes the overall window style: shape, density and border character."));
        addInstructionItem(activity, box, tr(prefs, "Настройка кнопок управления музыкой", "Music control buttons settings"), tr(prefs, "Размер, форма и расстояние между кнопками назад, пауза и вперёд.", "Size, shape and spacing of previous, pause and next buttons."));
        addInstructionItem(activity, box, tr(prefs, "Поведение окна при паузе", "Window behavior on pause"), tr(prefs, "Определяет, скрывать ли окно на паузе, оставлять его или убирать с задержкой.", "Defines whether the window hides on pause, stays visible, or hides after a short delay."));
        addInstructionItem(activity, box, tr(prefs, "Разрешения", "Permissions"), tr(prefs, "Раздел для выдачи системных разрешений, без которых окно и распознавание музыки не смогут работать стабильно.", "Section for granting the system permissions required for stable overlay and music detection."));
        addInstructionItem(activity, box, tr(prefs, "Поверх окон", "Draw over apps"), tr(prefs, "Разрешение на показ окна поверх навигатора и других приложений.", "Permission to show the window over navigation and other apps."));
        addInstructionItem(activity, box, tr(prefs, "Доступ к уведомлениям", "Notification access"), tr(prefs, "Нужен для чтения уведомлений плееров и получения информации о треках.", "Required to read player notifications and obtain track information."));
        addInstructionItem(activity, box, tr(prefs, "Уведомления приложения", "App notifications"), tr(prefs, "Дополнительное разрешение Android 13+, чтобы само приложение могло показывать свои уведомления.", "Extra Android 13+ permission so the app can show its own notifications."));
        addInstructionItem(activity, box, tr(prefs, "Специальные возможности", "Accessibility"), tr(prefs, "Помогают точнее понимать, что сейчас находится на экране магнитолы или телефона.", "Helps the app more accurately understand what is currently on the screen."));
        addInstructionItem(activity, box, tr(prefs, "История использования", "Usage access"), tr(prefs, "Запасной способ определить активное приложение, если системный экран меняется нестандартно.", "Fallback way to detect the active app if the system screen changes in a non-standard way."));
        addInstructionItem(activity, box, tr(prefs, "Диагностика", "Diagnostics"), tr(prefs, "Помогает понять, почему окно не появляется, и позволяет быстро скопировать отчёт.", "Helps you understand why the window does not appear and lets you quickly copy a report."));
        addInstructionItem(activity, box, tr(prefs, "Показывать окно постоянно", "Show window continuously"), tr(prefs, "Окно не скрывается по таймеру, пока трек считается активным.", "The window does not hide by timer while the track is considered active."));
        addInstructionItem(activity, box, tr(prefs, "Полоса перемотки трека", "Track seek bar"), tr(prefs, "Появляется под текстом и позволяет перематывать трек пальцем. Работает только вместе с постоянным показом и только там, где плеер поддерживает перемотку.", "Appears under the text and lets you seek the track with your finger. Works only with continuous display and only where the player supports seeking."));
        addInstructionItem(activity, box, tr(prefs, "Показывать обложку в окне", "Show album art in the window"), tr(prefs, "Добавляет обложку слева от текста, если плеер действительно передаёт картинку трека.", "Adds album art to the left of the text if the player actually provides track artwork."));
        addInstructionItem(activity, box, tr(prefs, "Тапы по обложке", "Album art taps"), tr(prefs, "Если обложка включена и кнопки управления выключены, одиночный тап ставит музыку на паузу, а двойной тап открывает текущий плеер.", "If album art is enabled and control buttons are disabled, a single tap pauses the music and a double tap opens the current player."));
        addInstructionItem(activity, box, tr(prefs, "Кнопки управления музыкой в overlay", "Media control buttons in overlay"), tr(prefs, "Добавляет в окно кнопки назад, пауза и вперёд прямо поверх навигатора.", "Adds previous, pause and next buttons directly inside the window over navigation."));
        addInstructionItem(activity, box, tr(prefs, "Свайп по overlay: влево/вправо переключает треки", "Swipe on overlay: left/right switches tracks"), tr(prefs, "Позволяет перелистывать треки быстрым движением по самому окну.", "Lets you switch tracks with a quick swipe directly on the window."));
        addInstructionItem(activity, box, tr(prefs, "Snap: магнит к краям и центру после перетаскивания", "Snap: magnet to edges and center after dragging"), tr(prefs, "После перетаскивания окно само прилипает к удобным зонам экрана.", "After dragging, the window automatically snaps to convenient screen zones."));
        addInstructionItem(activity, box, tr(prefs, "При изменении громкости временно приглушать окно", "Temporarily dim the window on volume change"), tr(prefs, "Во время регулировки громкости окно становится менее заметным и потом возвращает обычную яркость.", "While adjusting volume, the window becomes less visible and then returns to normal brightness."));
        addInstructionItem(activity, box, tr(prefs, "Floating режим: показывать трек даже без навигатора", "Floating mode: show the track even without navigation"), tr(prefs, "Разрешает показывать окно даже тогда, когда навигатор не открыт.", "Allows the window to appear even when navigation is not open."));
        addInstructionItem(activity, box, tr(prefs, "Сворачивать окно вместе с навигацией", "Hide the window together with navigation"), tr(prefs, "Когда навигация уходит с экрана, overlay тоже прячется.", "When navigation leaves the screen, the overlay hides as well."));
        addInstructionItem(activity, box, tr(prefs, "Фиксированный размер окна", "Fixed window size"), tr(prefs, "Фиксирует ширину окна и позволяет не зависеть от длины текста.", "Locks the window width so it does not depend on text length."));
        addInstructionItem(activity, box, tr(prefs, "Мягкое восстановление после системных окон", "Soft recovery after system windows"), tr(prefs, "Для проблемных магнитол. После громкости или чужих окон приложение восстанавливает overlay мягче и спокойнее.", "For problematic head units. After volume or other windows, the app restores the overlay more gently."));
        addInstructionItem(activity, box, tr(prefs, "История последних треков", "Recent track history"), tr(prefs, "Показывает последние 30 треков и позволяет быстро копировать названия.", "Shows the latest 30 tracks and lets you quickly copy track names."));
        addInstructionItem(activity, box, tr(prefs, "Скопировать отчёт", "Copy report"), tr(prefs, "Сохраняет технический отчёт в буфер обмена, чтобы проще было отправить его для разбора.", "Copies a technical report to the clipboard so it is easier to send for troubleshooting."));
        addInstructionItem(activity, box, tr(prefs, "Проверить окно", "Preview window"), tr(prefs, "Показывает тестовое окно на 5 секунд, чтобы быстро увидеть изменения.", "Shows a test window for 5 seconds so you can quickly preview changes."));
        addInstructionItem(activity, box, tr(prefs, "Проверить и восстановить", "Check and recover"), tr(prefs, "Открывает нужные настройки, если приложению реально не хватает разрешений.", "Opens the needed settings if the app is really missing permissions."));

        outer.addView(secondaryButton(activity, tr(prefs, "Готово", "Done"), v -> dialog.dismiss()), btnLp(activity));
        dialog.show();
        fitDialog(dialog, true);
    }

    static void showSetupInstructionsDialog(Activity activity, Prefs prefs) {
        final Dialog dialog = fullDialog(activity);
        LinearLayout outer = dialogRoot(activity);
        dialog.setContentView(outer);
        outer.addView(tv(activity, tr(prefs, "Инструкция по настройкам", "Setup instructions"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(activity, tr(prefs, "Что нужно включить, чтобы Navi Overlay работал корректно и не терялся в фоне.", "What to enable so Navi Overlay works correctly and stays alive in the background."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(activity, outer, 12);

        ScrollView sv = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addInstructionItem(activity, box,
                tr(prefs, "1. Выдать все основные разрешения", "1. Grant all main permissions"),
                tr(prefs,
                        "Для стабильной работы нужно включить: «Поверх окон», «Доступ к уведомлениям», «Уведомления приложения», «Специальные возможности» и «История использования». Без этих разрешений окно, музыка и активное приложение могут определяться нестабильно.",
                        "For stable operation enable: Draw over apps, Notification access, App notifications, Accessibility and Usage access. Without them, the overlay, music and active app detection may work unreliably."));

        addInstructionItem(activity, box,
                tr(prefs, "2. Не давать системе усыплять приложение", "2. Prevent the system from putting the app to sleep"),
                tr(prefs,
                        "На многих магнитолах приложение нужно добавить в исключение для сна или автозакрытия. Обычно это делается в настройках системы или в заводских настройках магнитолы.",
                        "On many head units you should add the app to the sleep or auto-close exclusion list. This is usually done in system settings or the head unit factory settings."));

        addInstructionItem(activity, box,
                tr(prefs, "3. Проверить выбранные приложения", "3. Check selected apps"),
                tr(prefs,
                        "Убедись, что нужные навигаторы добавлены в «Приложения навигации», а нужные плееры — в «Аудио приложения». Иначе overlay может не появляться там, где ты его ожидаешь.",
                        "Make sure your navigation apps are added to Navigation apps and your players are added to Audio apps. Otherwise the overlay may not appear where you expect it."));

        addInstructionItem(activity, box,
                tr(prefs, "4. Для проблемных магнитол", "4. For problematic head units"),
                tr(prefs,
                        "Если окно пропадает после громкости, системных окон или восстановления экрана, попробуй включить «Мягкое восстановление после системных окон».",
                        "If the window disappears after volume UI, system windows or screen restoration, try enabling Soft recovery after system windows."));

        addInstructionItem(activity, box,
                tr(prefs, "5. После изменения настроек", "5. After changing settings"),
                tr(prefs,
                        "После выдачи разрешений и изменения важных функций лучше один раз открыть «Проверить и восстановить» или перезапустить сервис приложения.",
                        "After granting permissions and changing important options, it is best to run Check and recover once or restart the app service."));

        outer.addView(secondaryButton(activity, tr(prefs, "Готово", "Done"), v -> dialog.dismiss()), btnLp(activity));
        dialog.show();
        fitDialog(dialog, true);
    }

    private static void addInstructionItem(Activity activity, LinearLayout parent, String title, String desc) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, activity));
        Ui.pad(row, 14, 12, 14, 12);
        row.addView(tv(activity, title, 15, TEXT, Typeface.BOLD, Gravity.START), matchWrap());
        TextView hint = tv(activity, desc, 13, MUTED, Typeface.NORMAL, Gravity.START);
        hint.setLineSpacing(Ui.dp(activity, 2), 1.0f);
        row.addView(hint, matchWrap());
        parent.addView(row, smallGapLp(activity));
    }

    private static Dialog fullDialog(Activity activity) {
        Dialog d = new Dialog(activity);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private static LinearLayout dialogRoot(Activity activity) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(gradient(activity, BG, BG_2, 0));
        Ui.pad(box, 18, 20, 18, 16);
        return box;
    }

    private static void fitDialog(Dialog d, boolean fullHeight) {
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, fullHeight ? -1 : -2);
        }
    }

    private static TextView tv(Activity activity, String s, int sp, int color, int style, int gravity) {
        TextView t = Ui.tv(activity, s, sp, color, style);
        t.setGravity(gravity);
        return t;
    }

    private static Button secondaryButton(Activity activity, String text, View.OnClickListener l) {
        Button b = new Button(activity);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setIncludeFontPadding(false);
        b.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        b.setBackground(Ui.stroke(CARD_2, 1, STROKE, 16, activity));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private static GradientDrawable gradient(Activity activity, int top, int bottom, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(Ui.dp(activity, radius));
        return g;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private static LinearLayout.LayoutParams btnLp(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 52));
        lp.setMargins(0, Ui.dp(activity, 12), 0, 0);
        return lp;
    }

    private static LinearLayout.LayoutParams smallGapLp(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, Ui.dp(activity, 9), 0, 0);
        return lp;
    }

    private static void space(Activity activity, LinearLayout parent, int dp) {
        Space s = new Space(activity);
        parent.addView(s, new LinearLayout.LayoutParams(1, Ui.dp(activity, dp)));
    }

    private static String tr(Prefs prefs, String ru, String en) {
        return prefs != null && prefs.englishUi() ? en : ru;
    }
}
