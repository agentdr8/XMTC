package com.dr8.xposedmtc.utils;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.dr8.xposedmtc.R;

import java.util.List;

import fr.rolandl.carousel.CarouselAdapter;
import fr.rolandl.carousel.CarouselItem;

public final class CoverAdapter extends CarouselAdapter<Cover> {

    public static final class CoverItem extends CarouselItem<Cover>
    {

        private ImageView image;
        private TextView name;
        private Context context;

        public CoverItem(Context context)
        {
            super(context, R.layout.ss_layout);
            this.context = context;
        }

        @Override
        public void extractView(View view)
        {
            image = (ImageView) view.findViewById(R.id.coverimage);
            name = (TextView) view.findViewById(R.id.covername);
        }

        @Override
        public void update(Cover cover)
        {
            image.setImageResource(getResources().getIdentifier(cover.image, "drawable", context.getPackageName()));
            name.setText(cover.name);
        }

    }

    public CoverAdapter(Context context, List<Cover> cover)
    {
        super(context, cover);
    }

    @Override
    public CarouselItem<Cover> getCarouselItem(Context context)
    {
        return new CoverItem(context);
    }

}
