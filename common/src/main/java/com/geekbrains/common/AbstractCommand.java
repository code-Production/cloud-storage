package com.geekbrains.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
@AllArgsConstructor
public abstract class AbstractCommand implements Serializable  {

    private Commands command;

}
