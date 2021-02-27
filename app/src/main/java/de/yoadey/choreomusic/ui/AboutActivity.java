package de.yoadey.choreomusic.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

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

        Element privacyElement = new Element();
        privacyElement.setIconDrawable(R.drawable.baseline_privacy_tip_24);
        privacyElement.setTitle(getString(R.string.privacy_policy));
        Intent privacyIntent = new Intent(Intent.ACTION_SENDTO);
        privacyIntent.setAction(Intent.ACTION_VIEW);
        privacyIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        privacyIntent.setData(Uri.parse("https://choreohelper.yoadey.de/choreo-music-helper-privacy-policy/"));
        privacyElement.setIntent(privacyIntent);

        Element licensesElement = new Element();
        licensesElement.setIconDrawable(R.drawable.baseline_description_24);
        licensesElement.setTitle(getString(R.string.licenses));
        Intent licensesIntent = new Intent(this, OssLicensesMenuActivity.class);
        licensesElement.setIntent(licensesIntent);

        View aboutView = new AboutPage(this)
                .isRTL(false)
                .enableDarkMode(false)
                .setImage(R.drawable.ic_launcher_foreground)
                .setDescription(getString(R.string.app_description))
                .setDescription(getString(R.string.about_description))
                .addGroup(getString(R.string.contact_group))
                .addEmail("info@yoadey.de")
                .addGitHub("https://github.com/yoadey/choreomusic")
                .addItem(privacyElement)
                .addItem(licensesElement)
                .addItem(versionElement)
                .create();
        LinearLayout layout = findViewById(R.id.about_layout);
        layout.addView(aboutView);
    }
}