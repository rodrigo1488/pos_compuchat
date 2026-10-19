package com.compumais.poscompuchat.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.compumais.poscompuchat.R;
import com.compumais.poscompuchat.data.CatalogModels;
import com.compumais.poscompuchat.data.ImageStore;

import java.util.List;
import java.util.Locale;

class CartListAdapter extends RecyclerView.Adapter<CartListAdapter.VH> {
    interface Host {
        CatalogModels.ProductDto findProduct(int id);
        void onEditObservation(CatalogModels.CartLine line);
        void onRemoveLine(int index);
    }

    private final List<CatalogModels.CartLine> cart;
    private final Host host;

    CartListAdapter(List<CatalogModels.CartLine> cart, Host host) {
        this.cart = cart;
        this.host = host;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CatalogModels.CartLine line = cart.get(position);
        holder.name.setText(line.quantity + "x " + line.productName);
        StringBuilder detail = new StringBuilder();
        if ("halfAndHalf".equals(line.type)) {
            CatalogModels.ProductDto p1 = host.findProduct(line.half1ProductId == null ? 0 : line.half1ProductId);
            CatalogModels.ProductDto p2 = host.findProduct(line.half2ProductId == null ? 0 : line.half2ProductId);
            CatalogModels.OptionDto opt1 = matchingOption(p1, null);
            if (line.half1OptionId != null && p1 != null && p1.variations != null
                    && !p1.variations.isEmpty() && p1.variations.get(0).options != null) {
                for (CatalogModels.OptionDto o : p1.variations.get(0).options) {
                    if (o.id == line.half1OptionId) {
                        opt1 = o;
                        break;
                    }
                }
            }
            double v1 = flavorValue(p1, opt1);
            double v2 = flavorValue(p2, opt1);
            detail.append("1/2 ").append(line.half1Name == null ? "" : line.half1Name)
                    .append("  ").append(money(v1)).append("\n");
            detail.append("1/2 ").append(line.half2Name == null ? "" : line.half2Name)
                    .append("  ").append(money(v2));
            if (line.optionLabel != null && !line.optionLabel.isEmpty()) {
                detail.append("\n").append(line.optionLabel);
            }
            holder.halfImages.setVisibility(View.VISIBLE);
            ImageStore.bind(holder.imgHalf1, holder.itemView.getContext(),
                    p1 == null ? null : p1.imageId);
            ImageStore.bind(holder.imgHalf2, holder.itemView.getContext(),
                    p2 == null ? null : p2.imageId);
        } else {
            holder.halfImages.setVisibility(View.GONE);
            if (line.optionLabel != null && !line.optionLabel.isEmpty()) {
                detail.append(line.optionLabel);
            }
        }
        if (line.addons != null && !line.addons.isEmpty()) {
            if (detail.length() > 0) detail.append("\n");
            for (int i = 0; i < line.addons.size(); i++) {
                if (i > 0) detail.append(", ");
                detail.append(line.addons.get(i).label);
            }
        }
        if (line.observation != null && !line.observation.trim().isEmpty()) {
            if (detail.length() > 0) detail.append("\n");
            detail.append("Obs: ").append(line.observation.trim());
        }
        holder.detail.setText(detail.length() == 0 ? "Toque em Obs para anotar o pedido" : detail.toString());
        holder.price.setText(money(line.lineTotal()));
        holder.obs.setOnClickListener(v -> {
            int idx = holder.getBindingAdapterPosition();
            if (idx != RecyclerView.NO_POSITION && idx < cart.size()) {
                host.onEditObservation(cart.get(idx));
            }
        });
        holder.remove.setOnClickListener(v -> {
            int idx = holder.getBindingAdapterPosition();
            if (idx != RecyclerView.NO_POSITION && idx < cart.size()) {
                host.onRemoveLine(idx);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cart.size();
    }

    static CatalogModels.OptionDto matchingOption(CatalogModels.ProductDto p,
                                                  CatalogModels.OptionDto baseOpt) {
        if (p == null || p.variations == null || p.variations.isEmpty()
                || p.variations.get(0).options == null
                || p.variations.get(0).options.isEmpty()) {
            return null;
        }
        List<CatalogModels.OptionDto> opts = p.variations.get(0).options;
        if (baseOpt != null && baseOpt.label != null) {
            for (CatalogModels.OptionDto o : opts) {
                if (baseOpt.label.equalsIgnoreCase(o.label)) {
                    return o;
                }
            }
        }
        return opts.get(0);
    }

    static double flavorValue(CatalogModels.ProductDto p, CatalogModels.OptionDto baseOpt) {
        CatalogModels.OptionDto opt = matchingOption(p, baseOpt);
        return opt != null ? opt.value : (p == null ? 0 : p.value);
    }

    static String money(double value) {
        return "R$ " + String.format(Locale.US, "%.2f", value);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, detail, price;
        View obs, remove, halfImages;
        ImageView imgHalf1, imgHalf2;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.txtCartName);
            detail = v.findViewById(R.id.txtCartDetail);
            price = v.findViewById(R.id.txtCartPrice);
            obs = v.findViewById(R.id.btnCartObs);
            remove = v.findViewById(R.id.btnCartRemove);
            halfImages = v.findViewById(R.id.boxHalfImages);
            imgHalf1 = v.findViewById(R.id.imgHalf1);
            imgHalf2 = v.findViewById(R.id.imgHalf2);
        }
    }
}
