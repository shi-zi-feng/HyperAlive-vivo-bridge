package com.qihang.vivobridge;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** A launcher and read-only status page; opening it never changes system settings. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(25, 58, 54);
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 247, 242));
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    @Override public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        text(content, "HYPERALIVE / VIVO", 13, true);
        text(content, "熄屏兼容层", 30, true);
        text(content, "iQOO · OriginOS 兼容测试", 16, false);

        LinearLayout status = card();
        text(status, "设备与安装状态", 19, true);
        text(status, Build.MANUFACTURER + " " + Build.MODEL
                + "  ·  Android " + Build.VERSION.RELEASE, 15, false);
        text(status, "兼容层版本：" + version(getPackageName()), 15, false);
        text(status, "原版 HyperAlive：" + version("com.qihang.aod"), 15, false);
        String aod;
        try {
            int value = Settings.System.getInt(getContentResolver(), "screen_off_remind");
            aod = value == 1 ? "已开启" : value == 0 ? "已关闭" : "系统值 " + value;
        } catch (Exception unavailable) {
            aod = "无法读取";
        }
        text(status, "系统熄屏显示开关：" + aod, 15, false);

        LinearLayout help = card();
        text(help, "模块运行状态待验证", 19, true);
        text(help, "此页面只能确认应用安装情况，无法确认 LSPosed 注入成功。"
                + "熄屏效果仍需通过日志和实际显示验证。", 15, false);
        text(help, "请在 LSPosed 中启用本兼容层，作用域选择：系统框架、系统界面、"
                + "com.vivo.nightpearl（原生熄屏组件）。同时保留原版 HyperAlive。", 15, false);
        text(help, "壁纸和熄屏样式请在原版 HyperAlive 中设置。", 15, false);
        text(help, "本版尝试恢复 vivo 原生熄屏电源流程，并在模块熄屏时钟可见后停止绘制系统时间和日期，"
                + "防止两套时钟重叠。模块时钟未就绪时保留系统时钟。实际常驻效果尚待真机验证。", 15, false);

        Button open = new Button(this);
        open.setText("打开原版 HyperAlive");
        open.setAllCaps(false);
        open.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.qihang.aod");
            try {
                if (intent == null) throw new IllegalStateException();
                startActivity(intent);
            } catch (Exception unavailable) {
                Toast.makeText(this, "未找到原版 HyperAlive 的可用入口", Toast.LENGTH_LONG).show();
            }
        });
        content.addView(open, new LinearLayout.LayoutParams(-1, -2));
        Button modules = new Button(this);
        modules.setText("打开 LSPosed 管理器");
        modules.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            try {
                if (intent == null) throw new IllegalStateException();
                startActivity(intent);
            } catch (Exception unavailable) {
                Toast.makeText(this, "请从 LSPosed 通知或快捷方式进入管理器", Toast.LENGTH_LONG).show();
            }
        });
        content.addView(modules, new LinearLayout.LayoutParams(-1, -2));
        Button refresh = new Button(this);
        refresh.setText("刷新安装状态");
        refresh.setOnClickListener(v -> render());
        content.addView(refresh, new LinearLayout.LayoutParams(-1, -2));
        text(content, "打开此应用不会重启手机或修改系统设置。", 13, false);
    }

    private String version(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName == null ? "已安装" : info.versionName;
        } catch (Exception unavailable) {
            return "未检测到安装";
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(12), dp(18), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(18));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(20);
        params.bottomMargin = dp(12);
        content.addView(card, params);
        return card;
    }

    private void text(LinearLayout parent, String value, int sp, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sp);
        label.setTextColor(INK);
        label.setLineSpacing(dp(3), 1f);
        label.setPadding(0, dp(7), 0, dp(7));
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
