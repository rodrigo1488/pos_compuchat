package com.compumais.poscompuchat.data;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class CatalogModels {
    public static class Catalog {
        public long catalogVersion;
        public String updatedAt;
        public List<UserDto> users = new ArrayList<>();
        public List<MesaDto> mesas = new ArrayList<>();
        public List<ProductDto> products = new ArrayList<>();
        public List<AddOnGroupDto> groups = new ArrayList<>();
        public List<String> productGroups = new ArrayList<>();
        public List<GrupoAddOnDto> grupoAddOn = new ArrayList<>();
        public List<ImageDto> images = new ArrayList<>();
        public boolean unchanged;
    }

    public static class ImageDto {
        public String id;
        public String hash;
        public String url;
    }

    public static class ContaResumo {
        public boolean open;
        public int numeromesa;
        public String cliente;
        public double valortotal;
        public List<ContaItem> itens = new ArrayList<>();
    }

    public static class ContaItem {
        public String nomeproduto;
        public double quantidade;
        public double valortotal;
        public double precounitario;
        public String observacao;
        public String hora;
        public Integer idproduto;
        public String codigoproduto;
    }

    public static class UserDto {
        public int id;
        public String name;
        public boolean pin;
        public String profile;
    }

    public static class MesaDto {
        public int id;
        public String number;
        public String name;
        public String type;
        public String status;
        public String contactName;
        @SerializedName("valortotal")
        public double totalValue;
        public int displayOrder;
        public String section;
    }

    public static class ProductDto {
        public int id;
        public String name;
        public String description;
        public double value;
        public String grupo;
        public boolean variablePrice;
        public boolean isCombo;
        public boolean allowsHalfAndHalf;
        public String halfAndHalfPriceRule;
        public String halfAndHalfGrupo;
        public String idUniplus;
        public String imageUrl;
        public String imageId;
        public Integer addOnGroupId;
        public List<VariationDto> variations = new ArrayList<>();
    }

    public static class VariationDto {
        public int id;
        public String name;
        public List<OptionDto> options = new ArrayList<>();
    }

    public static class OptionDto {
        public int id;
        public String label;
        public double value;
        public String idUniplus;
    }

    public static class AddOnGroupDto {
        public int id;
        public String name;
        public boolean required;
        public int minItems;
        public Integer maxItems;
        public List<AddOnItemDto> items = new ArrayList<>();
        public List<AddOnSubgroupDto> subgroups = new ArrayList<>();
    }

    public static class AddOnSubgroupDto {
        public int id;
        public String name;
        public boolean required;
        public int minItems;
        public Integer maxItems;
        public List<AddOnItemDto> items = new ArrayList<>();
    }

    public static class AddOnItemDto {
        public int id;
        public String label;
        public double value;
        public String idUniplus;
        public Integer subgroupId;
    }

    public static class GrupoAddOnDto {
        public String grupo;
        @SerializedName("addOnGroupId")
        public int addOnGroupId;
    }

    public static class CartAddon {
        public int addOnItemId;
        public String label;
        public double value;
        public String idUniplus;
        public int quantity = 1;
    }

    public static class CartLine {
        public String type = "normal";
        public int productId;
        public String productName;
        public double productValue;
        public int quantity = 1;
        public Integer optionId;
        public String optionLabel;
        public String grupo;
        public String idUniplus;
        public Integer half1ProductId;
        public Integer half2ProductId;
        public String half1Name;
        public String half2Name;
        public Integer half1OptionId;
        public Integer half2OptionId;
        public Integer baseOptionId;
        public String observation = "";
        public List<CartAddon> addons = new ArrayList<>();

        public double addonsTotal() {
            double add = 0;
            if (addons == null) {
                return 0;
            }
            for (CartAddon a : addons) {
                add += a.value * Math.max(1, a.quantity);
            }
            return add;
        }

        public double lineTotal() {
            return (productValue + addonsTotal()) * quantity;
        }
    }

    public static class OrderResult {
        public boolean ok;
        public Boolean printed;
        public boolean reused;
        public String printError;
        public String protocol;
        public String error;
    }

    public static class PedidoDia {
        public List<PedidoPrinter> printers = new ArrayList<>();
        public List<PedidoItem> itens = new ArrayList<>();
    }

    public static class PedidoPrinter {
        public String deviceId;
        public String name;
    }

    public static class PedidoItem {
        public int id;
        public int numeromesa;
        public String cliente;
        public String nomeproduto;
        public double quantidade;
        public String observacao;
        public String hora;
        public String grupo;
        public String printerDeviceId;
        public String printerName;
        public boolean entregue;
    }

    public static class CancelFilaResult {
        public boolean ok;
        public String error;
        public List<CancelFilaPrinter> results = new ArrayList<>();
    }

    public static class CancelFilaPrinter {
        public String deviceId;
        public String name;
        public boolean ok;
        public String message;
    }
}
