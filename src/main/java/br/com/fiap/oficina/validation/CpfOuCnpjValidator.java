package br.com.fiap.oficina.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validator;
import org.hibernate.validator.constraints.br.CNPJ;
import org.hibernate.validator.constraints.br.CPF;
import org.springframework.beans.factory.annotation.Autowired;

public class CpfOuCnpjValidator implements ConstraintValidator<CpfOuCnpj, String> {

    @Autowired
    private Validator validator;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        return switch (value.length()) {
            case 11 -> validator.validate(new CpfHolder(value)).isEmpty();
            case 14 -> validator.validate(new CnpjHolder(value)).isEmpty();
            default -> false;
        };
    }

    private record CpfHolder(@CPF String value) {}
    private record CnpjHolder(@CNPJ String value) {}
}
