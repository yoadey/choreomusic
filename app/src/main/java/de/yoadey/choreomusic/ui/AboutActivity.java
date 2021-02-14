package de.yoadey.choreomusic.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.yoadey.choreomusic.R;
import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Element versionElement = new Element();
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionElement.setTitle("Version " + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.activity_about);

        View aboutView = new AboutPage(this)
                .isRTL(false)
                .setImage(R.drawable.ic_launcher_foreground)
                .setDescription(getString(R.string.app_description))
                .setDescription(getString(R.string.about_description))
                .addGroup(getString(R.string.contact_group))
                .addEmail("info@yoadey.de")
                .addGitHub("https://github.com/yoadey/choreomusic")
                .addItem(versionElement)
                .create();
        LinearLayout layout = findViewById(R.id.about_layout);
        layout.addView(aboutView);
    }
}