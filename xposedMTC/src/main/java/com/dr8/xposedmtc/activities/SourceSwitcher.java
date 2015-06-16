package com.dr8.xposedmtc.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.dr8.xposedmtc.R;
import com.dr8.xposedmtc.utils.Cover;
import com.dr8.xposedmtc.utils.CoverAdapter;

import java.util.ArrayList;
import java.util.List;

import fr.rolandl.carousel.Carousel;
import fr.rolandl.carousel.CarouselAdapter;
import fr.rolandl.carousel.CarouselBaseAdapter;

public class SourceSwitcher extends Activity implements CarouselBaseAdapter.OnItemClickListener, CarouselBaseAdapter.OnItemLongClickListener
{
    private Carousel carousel;

    private final List<Cover> covers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ss_main);

        carousel = (Carousel) findViewById(R.id.carousel);

        covers.add(new Cover("Photo1", "fotolia_40649376"));
        covers.add(new Cover("Photo2", "fotolia_40973414"));
        covers.add(new Cover("Photo3", "fotolia_48275073"));
        covers.add(new Cover("Photo4", "fotolia_50806609"));
        covers.add(new Cover("Photo5", "fotolia_61643329"));

        CarouselAdapter adapter = new CoverAdapter(this, covers);
        carousel.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        carousel.setOnItemClickListener(new CarouselBaseAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(CarouselBaseAdapter<?> carouselBaseAdapter, View view, int position, long l) {
                Toast.makeText(getApplicationContext(), "The item '" + position + "' has been clicked", Toast.LENGTH_SHORT).show();
                carousel.scrollToChild(position);
            }
        });

        carousel.setOnItemLongClickListener(new CarouselBaseAdapter.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(CarouselBaseAdapter<?> carouselBaseAdapter, View view, int position, long id) {
                Toast.makeText(getApplicationContext(), "The item '" + position + "' has been long clicked", Toast.LENGTH_SHORT).show();
                carousel.scrollToChild(position);
                return false;
            }

        });
    }

    @Override
    public void onItemClick(CarouselBaseAdapter<?> parent, View view, int position, long id)
    {
        Toast.makeText(getApplicationContext(), "The item '" + position + "' has been clicked", Toast.LENGTH_SHORT).show();
        carousel.scrollToChild(position);
    }

    @Override
    public boolean onItemLongClick(CarouselBaseAdapter<?> parent, View view, int position, long id)
    {
        Toast.makeText(getApplicationContext(), "The item '" + position + "' has been long clicked", Toast.LENGTH_SHORT).show();
        carousel.scrollToChild(position);
        return false;
    }

}
