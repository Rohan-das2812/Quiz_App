package com.example.quizzer;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.animation.Animator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class QuestionActivity extends AppCompatActivity {

    public static final String FILE_NAME = "QUIZZER";
    public static final String KEY_NAME = "QUESTIONS";

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef = database.getReference();

    private TextView question,noIndicator;
    private FloatingActionButton bookmarkBtn;
    private LinearLayout optionContainer;
    private Button shareBtn,nextBtn;
    private int count = 0;
    private List<QuestionModel> list;
    private int position = 0;
    private int score = 0;
    private String category;
    private int setNo;
    private Dialog loadingDialog;

    private List<QuestionModel> bookmarksList;

    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private Gson gson;
    private int matchedQuestionPosition;

    private InterstitialAd interstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadAds();

        question = findViewById(R.id.question);
        noIndicator = findViewById(R.id.no_indicator);
        bookmarkBtn = findViewById(R.id.bookmark_btn);
        optionContainer = findViewById(R.id.options_container);
        shareBtn = findViewById(R.id.share_btn);
        nextBtn = findViewById(R.id.next_btn);

        preferences = getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        editor = preferences.edit();
        gson = new Gson();

        getBookmarks();

        bookmarkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (modelMatch()){
                    bookmarksList.remove(matchedQuestionPosition);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        bookmarkBtn.setImageDrawable(getDrawable(R.drawable.bookmark_border));
                    }
                }else{
                    bookmarksList.add(list.get(position));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        bookmarkBtn.setImageDrawable(getDrawable(R.drawable.bookmark));
                    }

                }
            }
        });

        category = getIntent().getStringExtra("category");
        setNo = getIntent().getIntExtra("setNo",1);


        loadingDialog = new Dialog(this);
        loadingDialog.setContentView(R.layout.loading);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loadingDialog.getWindow().setBackgroundDrawable(getDrawable(R.drawable.rounded_corners));
        }
        loadingDialog.getWindow().setLayout(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        loadingDialog.setCancelable(false);


        list = new ArrayList<>();

        loadingDialog.show();
        myRef.child("SETS").child(category).child("questions").orderByChild("setNo").equalTo(setNo).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    list.add(snapshot.getValue(QuestionModel.class));
                }
                if (list.size() > 0){


                    for(int i=0;i < 4;i++){
                        optionContainer.getChildAt(i).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    checkAnswer((Button) v);
                                }

                            }
                        });
                    }
                    playAnim(question,0,list.get(position).getQuestion());
                    nextBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            nextBtn.setEnabled(false);
                            nextBtn.setAlpha(0.7f);
                            enableOption(true);
                            position++;
                            if(position == list.size()){
                                if (interstitialAd.isLoaded()){
                                    interstitialAd.show();
                                    return;
                                }
                                Intent scoreIntent = new Intent(QuestionActivity.this,ScoreActivity.class);
                                scoreIntent.putExtra("score",score);
                                scoreIntent.putExtra("total",list.size());
                                startActivity(scoreIntent);
                                finish();
                                return;
                            }
                            count=0;
                            playAnim(question,0,list.get(position).getQuestion());
                        }
                    });

                    shareBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String body = list.get(position).getQuestion() + "\n" +
                                    list.get(position).getOptionA() + "\n" +
                                    list.get(position).getOptionB() + "\n" +
                                    list.get(position).getOptionC() + "\n" +
                                    list.get(position).getOptionD();
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT,"Compete the challenge");
                            shareIntent.putExtra(Intent.EXTRA_TEXT,body);
                            startActivity(Intent.createChooser(shareIntent,"Share via"));
                        }
                    });

                }else {
                    finish();
                    Toast.makeText(QuestionActivity.this, "no questions", Toast.LENGTH_SHORT).show();
                }
                loadingDialog.dismiss();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(QuestionActivity.this, databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                loadingDialog.dismiss();
                finish();
            }
        });




    }

    @Override
    protected void onPause() {
        super.onPause();
        storeBookmarks();
    }

    private void playAnim(final View view , final int value, final String data){
        view.animate().alpha(value).scaleX(value).scaleY(value).setDuration(500).setStartDelay(100).setInterpolator(new DecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (value == 0 && count<4){
                    String option = "";
                    if (count == 0){
                        option = list.get(position).getOptionA();


                    }else if(count == 1){
                        option = list.get(position).getOptionB();
                    }else if(count == 2){

                        option = list.get(position).getOptionC();
                    }else if(count == 3){

                        option = list.get(position).getOptionD();

                    }
                    playAnim(optionContainer.getChildAt(count),0,option);
                    count++;
                }

            }

            @Override
            public void onAnimationEnd(Animator animation) {

                if (value == 0){
                    try {
                        ((TextView)view).setText(data);
                        noIndicator.setText(position+1+"/"+list.size());
                        if (modelMatch()){

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                bookmarkBtn.setImageDrawable(getDrawable(R.drawable.bookmark));
                            }
                        }else{

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                bookmarkBtn.setImageDrawable(getDrawable(R.drawable.bookmark_border));
                            }

                        }
                    }catch (ClassCastException ex){
                        ((Button)view).setText(data);
                    }
                    view.setTag(data);
                    playAnim(view,1,data);
                }

            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private void checkAnswer(Button selectedOption){
        enableOption(false);
        nextBtn.setEnabled(true);
        nextBtn.setAlpha(1);
        if(selectedOption.getText().toString().equals(list.get(position).getCorrectANS())){
            //correct
            score++;


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selectedOption.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            }

        }else {
            //incorrect


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selectedOption.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#ff0000")));
            }

            Button correctoption = (Button) optionContainer.findViewWithTag(list.get(position).getCorrectANS());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                correctoption.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            }


        }


    }
    private void enableOption(boolean enable){
        for(int i=0; i < 4 ;i++){
            optionContainer.getChildAt(i).setEnabled(enable);
            if (enable){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    optionContainer.getChildAt(i).setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8A8888")));
                }

            }
        }

    }

    private  void getBookmarks(){
        String json = preferences.getString(KEY_NAME,"");

        Type type = new TypeToken<List<QuestionModel>>(){}.getType();

        bookmarksList = gson.fromJson(json,type);

        if (bookmarksList == null){
            bookmarksList = new ArrayList<>();
        }
    }


    private boolean modelMatch(){
        boolean matched = false;
        int i=0;
        for (QuestionModel model : bookmarksList){
            if (model.getQuestion().equals(list.get(position).getQuestion())
            && model.getCorrectANS().equals(list.get(position).getCorrectANS())
            && model.getSetNo() == list.get(position).getSetNo()){
                matched = true;
                matchedQuestionPosition=i;
            }
            i++;
        }
        return matched;
    }

    private void storeBookmarks(){
        String json = gson.toJson(bookmarksList);

        editor.putString(KEY_NAME,json);
        editor.commit();
    }

    private  void loadAds(){
        AdView mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getResources().getString(R.string.interstitialAd_id));
        interstitialAd.loadAd(new AdRequest.Builder().build());
        interstitialAd.setAdListener(new AdListener(){
            @Override
            public void onAdClosed() {
                super.onAdClosed();
                interstitialAd.loadAd(new AdRequest.Builder().build());
                Intent scoreIntent = new Intent(QuestionActivity.this,ScoreActivity.class);
                scoreIntent.putExtra("score",score);
                scoreIntent.putExtra("total",list.size());
                startActivity(scoreIntent);
                finish();
                return;
            }
        });
    }
}