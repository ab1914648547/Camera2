package org.yl.mycamera.util;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

public class FragmentAdapters extends FragmentStateAdapter {

    List<Fragment> fragments;

    public FragmentAdapters(@NonNull FragmentActivity fragmentActivity, List<Fragment> fragments) {
        super(fragmentActivity);
        this.fragments = fragments;
    }


    @NonNull
    @Override
    public Fragment createFragment(int position) {

        if (position == 0){
            fragments.get(1).onDestroy();
        }else {
            fragments.get(0).onDestroy();
        }

        return fragments == null ? null : fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return fragments == null ? 0 : fragments.size();
    }
}
