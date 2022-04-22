package com.example.videoclassifier;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.example.videoclassification.ClassificationResult;
import com.example.videoclassification.VideoClassifier;

import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private ImageSwitcher imgSwitcher;
    private Button selectImgBtn, prevImgBtn, nextImgBtn;
    private TextView imageNum, videoResult, frameResult;

    private Uri[] imgURIArray;
    private VideoClassifier videoClassifier;

    private int pos = -1;
    private int numImage = 0;

    private String inferenceTime;

    private final int SELECT_IMG_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgSwitcher = findViewById(R.id.imgSwitcher);
        selectImgBtn = findViewById(R.id.selectImgBtn);
        prevImgBtn = findViewById(R.id.prevImgBtn);
        nextImgBtn = findViewById(R.id.nextImgBtn);

        // Initialize game classifier
        videoClassifier = new VideoClassifier(this);

        // Initialize image switcher
        imgSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                ImageView imgView = new ImageView(getApplicationContext());
                imgView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imgView.setLayoutParams(new ImageSwitcher.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
                return imgView;
            }
        });

        selectImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImageIntent();
            }
        });

        prevImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pos>0) {
                    pos--;
                    updateDisplayImage(imgURIArray[pos]);
                }
            }
        });

        nextImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pos!=-1) {
                    if (pos<(imgURIArray.length-1)) {
                        pos++;
                        updateDisplayImage(imgURIArray[pos]);
                    }
                }
            }
        });
    }

    private void selectImageIntent() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select image(s)"), SELECT_IMG_CODE);
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);

        if (reqCode == SELECT_IMG_CODE) {
            if (resCode == Activity.RESULT_OK) {
                if (data.getClipData()!=null) { // Multiple images
                    pos = 0;
                    numImage = data.getClipData().getItemCount();
                    imgURIArray = new Uri[numImage];
                    Bitmap[] imgBitmapArray = new Bitmap[numImage];

                    // Get image URIs and bitmaps
                    for (int i=0; i<numImage; i++) {
                        final Uri imageURI = data.getClipData().getItemAt(i).getUri();
                        imgURIArray[i] = imageURI;
                        imgBitmapArray[i] = getImageBitmap(imageURI);

                        // Display first image
                        if (i==0) {
                            updateDisplayImage(imageURI);
                        }
                    }

                    // Run inference
                    final long startTime = System.nanoTime();
                    final ClassificationResult result = videoClassifier.predictMultipleImages(imgBitmapArray, 2);
                    inferenceTime = String.format("%d ms", (System.nanoTime()-startTime)/1000000);

                    // Output results
                    printResult(imgBitmapArray[0], result);

                } else { // Single image
                    pos = 0;
                    numImage = 1;

                    // Get image URI and bitmap
                    Uri imgURI = data.getData();
                    imgURIArray = new Uri[] {imgURI};
                    final Bitmap imgBitmap = getImageBitmap(imgURI);

                    // Display image
                    updateDisplayImage(imgURI);

                    // Run inference
                    final long startTime = System.nanoTime();
                    final ClassificationResult result = videoClassifier.predictSingleImage(imgBitmap, 2);
                    inferenceTime = String.format("%d ms", (System.nanoTime()-startTime)/1000000);

                    // Output results
                    printResult(imgBitmap, result);
                }
            }
        }
    }

    private Bitmap getImageBitmap(Uri imgURI) {
        Bitmap imgBitmap = null;
        try {
            imgBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imgURI);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imgBitmap;
    }

    private void updateDisplayImage(Uri imgURI) {
        // Update image
        imgSwitcher.setImageURI(imgURI);

        // Run inference and update results
        final ClassificationResult result = videoClassifier.predictSingleImage(getImageBitmap(imgURI), 1);
        final String videoContent = result.getPrediction().get("VideoContent")[0];
        final String gameTitle = (result.getPrediction().get("GameTitle")[0]!=null) ? ("("+TextUtils.join(", ", result.getPrediction().get("GameTitle"))+")") : "";
        final String frameResultText = String.format("当前帧预测结果: %s %s", videoContent, gameTitle);
        frameResult = findViewById(R.id.frameResult);
        frameResult.setText(frameResultText);

        final String imageNumText = String.format("%d/%d", pos+1, numImage);
        imageNum = findViewById(R.id.imageNum);
        imageNum.setText(imageNumText);
    }

    private void printResult(Bitmap imgBitmap, ClassificationResult result) {
        final String videoContent = result.getPrediction().get("VideoContent")[0];
        final String gameTitle = (result.getPrediction().get("GameTitle")[0]!=null) ? ("("+TextUtils.join(", ", result.getPrediction().get("GameTitle"))+")") : "";

        final String videoResultText = String.format("视频预测结果: %s %s \n 像素: %dx%d px \n 抽帧数量: %d \n 计算时间: %s", videoContent, gameTitle, imgBitmap.getWidth(), imgBitmap.getHeight(), numImage, inferenceTime);
        videoResult = findViewById(R.id.videoResult);
        videoResult.setText(videoResultText);
    }
}