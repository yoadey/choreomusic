package de.yoadey.choreomusic.ui;

import android.os.Bundle;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import de.yoadey.choreomusic.R;
import de.yoadey.choreomusic.utils.Utils;
import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Element versionElement = new Element();
        versionElement.setTitle("Version 1.0.0");

        setContentView(R.layout.activity_about);

        View aboutView = new AboutPage(getApplicationContext())
                .isRTL(false)
                .setDescription(getString(R.string.app_description))
                .addGroup(getString(R.string.contact_group))
                .addEmail("contact@yoadey.de", "Email")
                .addGroup(getString(R.string.about_contact_us))
                .addItem(versionElement)
                .create();
        LinearLayout layout = findViewById(R.id.about_layout);
        layout.addView(aboutView);
    }
}