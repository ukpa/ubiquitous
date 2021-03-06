/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.unnikrishnanpatel.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private String minTemp = "";
    private String maxTemp = "";
    private int weatherImageId = -1;
    private Paint backgroundPaint;
    private Paint textPaint;
    private Paint dateText;
    private Paint maxTempPaint;
    private Paint minTempPaint;
    private Paint separatorLine;
    private Paint weatherImage;
    private Time time;
    private Calendar calendarTime;
    private float mYOffset;
    private float mDateYOffSet;
    private String[] mDaysOfWeek;
    private String[] mMonthsOfYear;
    private Bitmap mWeatherBitmap = null;




    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,ResultCallback<DataItemBuffer>,
    GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        private GoogleApiClient googleApiClient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        public Engine() {
            super();
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(googleApiClient,this);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(this);

        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo("/weather_data") == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        minTemp = dataMap.getString("min_temp");
                        maxTemp = dataMap.getString("max_temp");
                        weatherImageId = dataMap.getInt("weather_image_id");
                    }
                }
            }
            dataEventBuffer.release();
            if (isVisible() && !isInAmbientMode()) {
                invalidate();
            }

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onResult(@NonNull DataItemBuffer dataItems) {
            for (DataItem dataItem:dataItems){
                if (dataItem.getUri().getPath().compareTo("/weather_data") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    minTemp = dataMap.getString("min_temp");
                    maxTemp = dataMap.getString("max_temp");
                    weatherImageId = dataMap.getInt("weather_image_id");
                }
            }
            dataItems.release();
            if (isVisible() && !isInAmbientMode()) {
                invalidate();
            }

        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mDateYOffSet = resources.getDimension(R.dimen.digital_date_y_offset);


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.parseColor("#03A9F4"));


            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            dateText = createDate(resources.getColor(R.color.digital_text));
            separatorLine = createSeparatorLine(resources.getColor(R.color.digital_text));
            maxTempPaint = createMaxTemp(resources.getColor(R.color.digital_text));
            minTempPaint = createMinTemp(resources.getColor(R.color.digital_text));

            mTime = new Time();
            calendarTime = new GregorianCalendar();
            Date mCurrentDateTime = new Date();
            calendarTime.setTime(mCurrentDateTime);
            mDaysOfWeek = new DateFormatSymbols().getShortWeekdays();
            mMonthsOfYear = new DateFormatSymbols().getShortMonths();
            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this).
                    addApi(Wearable.API).addConnectionCallbacks(this).
                    addOnConnectionFailedListener(this).build();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if(googleApiClient != null && googleApiClient.isConnected()){
                Wearable.DataApi.removeListener(googleApiClient,this);
                googleApiClient.disconnect();
            }
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        // Create a paint using builder pattern
        private Paint createDate(int textColor) {
            Paint dataPaint = new Paint();
            dataPaint.setColor(textColor);
            dataPaint.setAntiAlias(true);

            dataPaint.setTextAlign(Paint.Align.CENTER);
            dataPaint.setStrokeWidth(2);
            return dataPaint;
        }

        private Paint createSeparatorLine(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStrokeWidth(1);
            return paint;
        }

        private Paint createMaxTemp(int textColor){
            Paint maxTempPaint = new Paint();
            maxTempPaint.setColor(textColor);
            maxTempPaint.setAntiAlias(true);
            maxTempPaint.setStrokeWidth(3);
            maxTempPaint.setTypeface(NORMAL_TYPEFACE);
            maxTempPaint.setTextAlign(Paint.Align.CENTER);

            return maxTempPaint;
        }

        private Paint createMinTemp(int textColor){
            Paint minTempPaint = new Paint();
            minTempPaint.setColor(textColor);
            minTempPaint.setAntiAlias(true);
            minTempPaint.setStrokeWidth(2);
            return minTempPaint;
        }

        private Paint createWeatherImagePaint(){
            Paint weatherImagePaint = new Paint();
            weatherImagePaint.setAntiAlias(true);
            return weatherImagePaint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float maxTempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round:R.dimen.digital_temp_text_size);
            float minTempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_min_temp_text_size_round:R.dimen.digital_min_temp_text_size);

            mTextPaint.setTextSize(textSize);
            dateText.setTextSize(dateTextSize);
            minTempPaint.setTextSize(minTempTextSize);
            maxTempPaint.setTextSize(maxTempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }
        private int getWeatherIcon(int weatherId) {

            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String hourText = String.format("%d:%02d", mTime.hour, mTime.minute);
            String date = mDaysOfWeek[calendarTime.get(Calendar.DAY_OF_WEEK)] + " "
                    + mMonthsOfYear[calendarTime.get(Calendar.MONTH)] + " "
                    + calendarTime.get(Calendar.DATE) + " "
                    + calendarTime.get(Calendar.YEAR);


            canvas.drawText(hourText, bounds.width() / 2, mYOffset, mTextPaint);
            canvas.drawText(date.toUpperCase()
                    ,bounds.width() / 2
                    ,mDateYOffSet
                    ,dateText);

            canvas.drawText(maxTemp,bounds.width()/2,
                    bounds.height()/2+60
                    ,maxTempPaint);

            canvas.drawText(minTemp
                    , bounds.width() / 2 + 35
                    , bounds.height() / 2 + 60
                    , minTempPaint);

            if(weatherImageId != -1){
                Bitmap icon = BitmapFactory.decodeResource(MyWatchFace.this.getResources()
                        ,getWeatherIcon(weatherImageId));
                Paint paint= new Paint();
                canvas.drawBitmap(icon, bounds.width() / 2 - MyWatchFace.this.getResources().getDimension(R.dimen.digital_weather_image_x_offset),
                        bounds.height() / 2 + MyWatchFace.this.getResources().getDimension(R.dimen.digital_weather_image_y_offset),
                        paint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
