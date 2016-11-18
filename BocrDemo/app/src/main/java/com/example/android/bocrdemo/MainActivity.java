package com.example.android.bocrdemo;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    final private static int RESULT_PICK_IMAGE = 2016;
    final private String BAIDU_OCR_URL = "http://word.bj.baidubce.com/api/v1/ocr/general";
    private ImageView imageView;
    private TextView wordsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        imageView = (ImageView) findViewById(R.id.imageView);
        wordsTextView = (TextView) findViewById(R.id.wordsTextView);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wordsTextView.setText("等待中...");
//                Snackbar.make(view, "等待OCR结果中", Snackbar.LENGTH_SHORT)
//                        .setAction("Action", null).show();
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, RESULT_PICK_IMAGE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_PICK_IMAGE && resultCode == RESULT_OK && null != data) {
            // get picture path.
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            final String picturePath = cursor.getString(columnIndex);
            cursor.close();
            // show picture on imageview.
            Bitmap bm = null;
            try {
                bm = autoRisizeFromLocalFile(picturePath);
                imageView.setImageBitmap(bm);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] imageData = baos.toByteArray();
                String imageB64 = Base64.encodeToString(imageData, Base64.DEFAULT).replace("\n", "");
                new OCRTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, imageB64);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String printOcr(HttpResponse response) {
        StringBuffer sb = new StringBuffer();
        try {
            String responseJson = EntityUtils.toString(response.getEntity());
            JSONObject responseJSON = new JSONObject(responseJson);

            if (responseJSON.has("words_result_num")) {
                int wordCount = responseJSON.getInt("words_result_num");
                sb.append("Total number: " + wordCount + "\n");
                JSONArray wordJsonsArray = responseJSON.getJSONArray("words_result");
                for (int i = 0; i < wordJsonsArray.length(); i++) {
                    JSONObject wordJson = wordJsonsArray.getJSONObject(i);
                    JSONObject positionJson = wordJson.getJSONObject("location");
                    int posX = positionJson.getInt("left");
                    int posY = positionJson.getInt("top");
                    String words = wordJson.getString("words");
                    sb.append("Pos: [" + posX + ", " + posY + "], Words: [" + words + "]\n");
                }
            } else if (responseJSON.has("error_code")) {
                sb.append("Error Code: " + responseJSON.getInt("error_code") + "\n");
                sb.append("Error Mesg: " + responseJSON.getString("error_msg") + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private Bitmap autoRisizeFromLocalFile(String picturePath) throws IOException {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(picturePath, options);
        options.inSampleSize = calculateInSampleSize(options, imageView.getWidth(), imageView.getHeight());
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(picturePath, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class OCRTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String imageB64 = params[0];
            try {
                final Date timestamp = new Date();
                HttpPost post = new HttpPost(BAIDU_OCR_URL);
                List<NameValuePair> nvp = new ArrayList<NameValuePair>();
                nvp.add(new BasicNameValuePair("image", imageB64));
                nvp.add(new BasicNameValuePair("detect_direction", "true"));
                post.setEntity(new UrlEncodedFormEntity(nvp, HTTP.UTF_8));
                BocSigner.signPost(post, timestamp);
                HttpClient client = new DefaultHttpClient();
                HttpResponse response = client.execute(post);
                return printOcr(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(String result) {
            wordsTextView.setText(result);
        }
    }
}
