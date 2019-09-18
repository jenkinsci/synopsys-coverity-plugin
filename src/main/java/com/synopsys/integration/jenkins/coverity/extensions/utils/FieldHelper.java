package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.util.stream.Stream;

import com.synopsys.integration.jenkins.coverity.extensions.CoveritySelectBoxEnum;
import com.synopsys.integration.log.IntLogger;

import hudson.util.ListBoxModel;

public abstract class FieldHelper {
    protected final IntLogger logger;

    public FieldHelper(final IntLogger logger) {
        this.logger = logger;
    }

    public static ListBoxModel getListBoxModelOf(final CoveritySelectBoxEnum[] coveritySelectBoxEnumValues) {
        return Stream.of(coveritySelectBoxEnumValues)
                   .collect(ListBoxModel::new, (model, value) -> model.add(value.getDisplayName(), value.name()), ListBoxModel::addAll);
    }

    protected ListBoxModel.Option wrapAsListBoxModelOption(final String nameValue) {
        return new ListBoxModel.Option(nameValue, nameValue, false);
    }

}
