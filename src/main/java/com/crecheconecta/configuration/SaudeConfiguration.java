package com.crecheconecta.configuration;

import com.crecheconecta.saude.application.port.in.AtualizarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.CadastrarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.ExcluirFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.ListarFichasSaudeUseCase;
import com.crecheconecta.saude.application.port.in.VisualizarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.out.ArmazenamentoArquivoPort;
import com.crecheconecta.saude.application.port.out.CriptografiaPort;
import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.application.port.out.VinculoResponsavelPort;
import com.crecheconecta.saude.application.service.AtualizarFichaSaudeService;
import com.crecheconecta.saude.application.service.CadastrarFichaSaudeService;
import com.crecheconecta.saude.application.service.ExcluirFichaSaudeService;
import com.crecheconecta.saude.application.service.ListarFichasSaudeService;
import com.crecheconecta.saude.application.service.PoliticaAcessoDadosSaude;
import com.crecheconecta.saude.application.service.VisualizarFichaSaudeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SaudeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PoliticaAcessoDadosSaude politicaAcessoDadosSaude(VinculoResponsavelPort vinculos) {
        return new PoliticaAcessoDadosSaude(vinculos);
    }

    @Bean
    CadastrarFichaSaudeUseCase cadastrarFichaSaude(
            FichaSaudeRepositoryPort repository,
            ArmazenamentoArquivoPort armazenamento,
            CriptografiaPort cripto,
            PoliticaAcessoDadosSaude politica,
            Clock clock
    ) {
        return new CadastrarFichaSaudeService(repository, armazenamento, cripto, politica, clock);
    }

    @Bean
    ListarFichasSaudeUseCase listarFichasSaude(
            FichaSaudeRepositoryPort repository,
            PoliticaAcessoDadosSaude politica
    ) {
        return new ListarFichasSaudeService(repository, politica);
    }

    @Bean
    VisualizarFichaSaudeUseCase visualizarFichaSaude(
            FichaSaudeRepositoryPort repository,
            CriptografiaPort cripto,
            PoliticaAcessoDadosSaude politica
    ) {
        return new VisualizarFichaSaudeService(repository, cripto, politica);
    }

    @Bean
    AtualizarFichaSaudeUseCase atualizarFichaSaude(
            FichaSaudeRepositoryPort repository,
            ArmazenamentoArquivoPort armazenamento,
            CriptografiaPort cripto,
            PoliticaAcessoDadosSaude politica,
            Clock clock
    ) {
        return new AtualizarFichaSaudeService(repository, armazenamento, cripto, politica, clock);
    }

    @Bean
    ExcluirFichaSaudeUseCase excluirFichaSaude(
            FichaSaudeRepositoryPort repository,
            ArmazenamentoArquivoPort armazenamento,
            PoliticaAcessoDadosSaude politica
    ) {
        return new ExcluirFichaSaudeService(repository, armazenamento, politica);
    }
}
