package me.takinrom.passwordmanager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    public interface OnClickListener {
        void onAccountClick(Account account);
    }

    private final LayoutInflater inflater;
    private final Account[] accounts;
    private final OnClickListener onClickListener;

    public AccountAdapter(@NonNull Context context, @NonNull Account[] accounts, OnClickListener onClickListener) {
        this.inflater = LayoutInflater.from(context);
        this.accounts = accounts;
        this.onClickListener = onClickListener;
    }

    @NonNull
    @Override
    public AccountAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.accounts_list_item, parent, false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(AccountAdapter.ViewHolder holder, int position) {
        Account account = accounts[position];
        holder.serviceView.setText(account.getService());
        holder.loginView.setText(account.getLogin());
        holder.itemView.setOnClickListener(v -> onClickListener.onAccountClick(account));
    }

    @Override
    public int getItemCount() {
        return accounts.length;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        final TextView serviceView, loginView;

        ViewHolder(View view) {
            super(view);
            serviceView = view.findViewById(R.id.serviceTextView);
            loginView = view.findViewById(R.id.loginTextView);
        }

    }
}
