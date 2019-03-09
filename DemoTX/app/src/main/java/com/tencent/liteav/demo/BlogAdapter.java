package com.tencent.liteav.demo;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.ViewHolder> {

    private List<String> question;
    private List<String> detail;

    private List<Integer> photo;
    private List<String> name;
    private List<String> topic;
    private List<String> time;

    private int rowLayout;
    private Context mContext;
    private View v;
    private BlogAdapter.OnItemClickListener onItemClickListener;

    public BlogAdapter(List<String> question,List<String> detail,List<Integer> photo,List<String> name, List<String> topic,List<String> time, int rowLayout, Context context) {
        this.question=question;
        this.detail=detail;

        this.photo=photo;
        this.name = name;
        this.topic=topic;
        this.time = time;

        this.rowLayout = rowLayout;
        this.mContext = context;
    }


    // ① 定义点击回调接口
    public interface OnItemClickListener {
        void onItemClick(View view, int position);
        //void onItemLongClick(View view, int position);
    }

    // ② 定义一个设置点击监听器的方法
    public void setOnItemClickListener(BlogAdapter.OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }


    public BlogAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        v = LayoutInflater.from(viewGroup.getContext()).inflate(rowLayout, viewGroup, false);
        return new BlogAdapter.ViewHolder(v);  //this is the major change here.
    }


    public int getItemCount() {
        return name == null ? 0 : name.size();
    }


    public void onBindViewHolder(final BlogAdapter.ViewHolder viewHolder, int position) {

        viewHolder.question.setText(question.get(position));
        viewHolder.detail.setText(detail.get(position));
        viewHolder.name.setText(name.get(position));
        viewHolder.topic.setText(topic.get(position));
        viewHolder.time.setText(time.get(position));

        viewHolder.photo.setImageResource(photo.get(position));


        //③ 对RecyclerView的每一个itemView设置点击事件
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if(onItemClickListener != null) {
                    int pos = viewHolder.getLayoutPosition();
                    onItemClickListener.onItemClick(viewHolder.itemView, pos);
                }
            }
        });

    }



    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView photo;
        public TextView question,detail,name,topic,time;

        public ViewHolder(View itemView) {
            super(itemView);
            photo= itemView.findViewById(R.id.photo);

            question=itemView.findViewById( R.id.question );
            detail=itemView.findViewById( R.id.detail );
            name =itemView.findViewById(R.id.name);
            topic=itemView.findViewById( R.id.topic );
            time =itemView.findViewById(R.id.time);
        }

    }
}


