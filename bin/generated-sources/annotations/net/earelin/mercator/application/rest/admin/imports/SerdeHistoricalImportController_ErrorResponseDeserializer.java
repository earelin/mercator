package net.earelin.mercator.application.rest.admin.imports;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.GeneratedSerdeExceptionUtil;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import java.io.IOException;
import java.lang.String;
import javax.annotation.processing.Generated;

@Prototype
@Generated("Micronaut")
public final class SerdeHistoricalImportController_ErrorResponseDeserializer implements Deserializer<HistoricalImportController.ErrorResponse> {
  private static final String KEY_0 = "status";

  private static final Argument ARGUMENT_0 = Argument.INT.withName(SerdeHistoricalImportController_ErrorResponseDeserializer.KEY_0);

  private static final String KEY_1 = "error";

  private static final Argument ARGUMENT_1 = Argument.STRING.withName(SerdeHistoricalImportController_ErrorResponseDeserializer.KEY_1);

  private static final String KEY_2 = "detail";

  private static final Argument ARGUMENT_2 = Argument.STRING.withName(SerdeHistoricalImportController_ErrorResponseDeserializer.KEY_2);

  private final boolean failOnNullForPrimitives;

  private final boolean ignoreUnknown;

  public SerdeHistoricalImportController_ErrorResponseDeserializer(@Parameter Deserializer.DecoderContext context, @Parameter Argument type) throws
      SerdeException {
    this.failOnNullForPrimitives = GeneratedSerdeExceptionUtil.failOnNullForPrimitives(context);
    this.ignoreUnknown = GeneratedSerdeExceptionUtil.ignoreUnknown(context);
  }

  public Deserializer<HistoricalImportController.ErrorResponse> createSpecific(Deserializer.DecoderContext context, Argument type) throws
      SerdeException {
    return (Deserializer<HistoricalImportController.ErrorResponse>) GeneratedSerdeFallbackUtil.withRuntimeObjectFallback(this, context, type);
  }

  public HistoricalImportController.ErrorResponse deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument type) throws
      IOException {
    Decoder objectDecoder = decoder.decodeObject(type);
    boolean seenProperty0 = false;
    boolean seenProperty1 = false;
    boolean seenProperty2 = false;
    int propertyValue0 = 0;
    String propertyValue1 = null;
    String propertyValue2 = null;
    while (true) {
      String key = objectDecoder.decodeKey();
      if (key == null) {
        objectDecoder.finishStructure();
        return new net.earelin.mercator.application.rest.admin.imports.HistoricalImportController.ErrorResponse(propertyValue0, propertyValue1, propertyValue2);
      }
      GeneratedSerdeExceptionUtil.PropertyDispatchResult propertyDispatchResult = switch (key) {
        case "status" -> {
          io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.HANDLED;
          if (seenProperty0) {
            dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.DUPLICATE;
          } else {
            seenProperty0 = true;
            try {
              if (this.failOnNullForPrimitives) {
                java.lang.Integer decodedValue0 = objectDecoder.decodeIntNullable();
                if (decodedValue0 == null) {
                  dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.NULL;
                } else {
                  propertyValue0 = decodedValue0;
                }
              } else {
                if (objectDecoder.decodeNull()) {
                } else {
                  propertyValue0 = objectDecoder.decodeInt();
                }
              }
            } catch (java.lang.Throwable e0) {
              throw io.micronaut.serde.util.GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, net.earelin.mercator.application.rest.admin.imports.SerdeHistoricalImportController_ErrorResponseDeserializer.ARGUMENT_0);
            }
          }
          yield dispatchResult;
        }
        case "error" -> {
          io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.HANDLED;
          if (seenProperty1) {
            dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.DUPLICATE;
          } else {
            seenProperty1 = true;
            try {
              propertyValue1 = objectDecoder.decodeStringNullable();
            } catch (java.lang.Throwable e0) {
              throw io.micronaut.serde.util.GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, net.earelin.mercator.application.rest.admin.imports.SerdeHistoricalImportController_ErrorResponseDeserializer.ARGUMENT_1);
            }
          }
          yield dispatchResult;
        }
        case "detail" -> {
          io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.HANDLED;
          if (seenProperty2) {
            dispatchResult = io.micronaut.serde.util.GeneratedSerdeExceptionUtil.PropertyDispatchResult.DUPLICATE;
          } else {
            seenProperty2 = true;
            try {
              propertyValue2 = objectDecoder.decodeStringNullable();
            } catch (java.lang.Throwable e0) {
              throw io.micronaut.serde.util.GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, net.earelin.mercator.application.rest.admin.imports.SerdeHistoricalImportController_ErrorResponseDeserializer.ARGUMENT_2);
            }
          }
          yield dispatchResult;
        }
        default -> GeneratedSerdeExceptionUtil.PropertyDispatchResult.UNKNOWN;
      };
      switch (propertyDispatchResult) {
        case GeneratedSerdeExceptionUtil.PropertyDispatchResult.NULL -> {
          throw GeneratedSerdeExceptionUtil.withPropertyPath(GeneratedSerdeExceptionUtil.nullValue(type, Argument.OBJECT_ARGUMENT.withName(key)), type, Argument.OBJECT_ARGUMENT.withName(key));
        }
        case GeneratedSerdeExceptionUtil.PropertyDispatchResult.UNKNOWN -> {
          if (this.ignoreUnknown) {
            objectDecoder.skipValue();
          } else {
            throw GeneratedSerdeExceptionUtil.unknownProperty(type, Argument.OBJECT_ARGUMENT.withName(key));
          }
        }
        case GeneratedSerdeExceptionUtil.PropertyDispatchResult.DUPLICATE -> {
          throw GeneratedSerdeExceptionUtil.duplicateProperty(type, Argument.OBJECT_ARGUMENT.withName(key));
        }
        case GeneratedSerdeExceptionUtil.PropertyDispatchResult.HANDLED -> {
        }
      }
    }
  }
}
