package no.vegvesen.nvdb.tnits.serialization

import com.esotericsoftware.kryo.Kryo
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.StedfestingLinje
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.model.Utstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.impl.CoordinateArraySequence

val kryo: Kryo =
    Kryo().apply {
        isRegistrationRequired = false // Keep disabled due to internal Java class complexity
        instantiatorStrategy = org.objenesis.strategy.StdInstantiatorStrategy()

        // Pre-register frequently used classes for better performance
        // This improves performance even without strict registration
        register(HashMap::class.java)
        register(LinkedHashMap::class.java)
        register(ArrayList::class.java)

        // Domain model classes
        register(Veglenke::class.java)
        register(VeglenkeId::class.java)
        register(Utstrekning::class.java)

        // Generated API enums and classes
        register(TypeVeg::class.java)
        register(Detaljniva::class.java)
        register(StedfestingLinje::class.java)

        // JTS Geometry classes
        register(LineString::class.java)
        register(Point::class.java)
        register(Coordinate::class.java)
        register(CoordinateSequence::class.java)
        register(CoordinateArraySequence::class.java)
        register(GeometryFactory::class.java)
        register(PrecisionModel::class.java)
    }
