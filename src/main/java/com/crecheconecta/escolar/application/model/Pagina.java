package com.crecheconecta.escolar.application.model;

import java.util.List;

public record Pagina<T>(List<T> itens, int pagina, int tamanho, int totalElementos, int totalPaginas) {

    public Pagina{
        itens = List.copyOf(itens);
    }

}
