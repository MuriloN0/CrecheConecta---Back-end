package com.crecheconecta.configuration;

import com.crecheconecta.escolar.application.port.in.GerenciarAlunoUseCase;
import com.crecheconecta.escolar.application.port.out.AlunoRepositoryPort;
import com.crecheconecta.escolar.application.service.AlunoService;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class EscolarConfiguration {
    @Bean
    GerenciarAlunoUseCase gerenciarAlunoUseCase(
            AlunoRepositoryPort repository
    ) {
        return new AlunoService(repository);
    }
}
