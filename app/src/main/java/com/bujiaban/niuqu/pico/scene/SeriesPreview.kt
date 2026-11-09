package com.bujiaban.niuqu.pico.scene

import com.bujiaban.niuqu.pico.domain.model.ToyDefinition
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** Catalogue miniatures use the real toy assets and never enter the owned inventory. */
class SeriesPreview(parent:Entity,templates:List<Entity>,private val definitions:List<ToyDefinition>) {
    val root=Entity().also{parent.addChild(it);it.setName("SeriesPreview");it.components[TransformComponent::class.java]?.setPosition(CapsuleLayout.seriesPreviewPosition)}
    private val models=templates.mapIndexed{index,template->
        requireNotNull(template.clone(Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))).also{model->
            root.addChild(model);model.setName("SeriesMiniature_$index");model.enabled=true
            val transform=requireNotNull(model.components[TransformComponent::class.java])
            transform.setEulerAngles(EulerAngles(0f,if(index%2==0)15f else -15f,0f))
            var bounds=model.getVisualBounds(root)
            transform.setScaleVector(Vector3(minOf(.048f/bounds.size.y,.043f/bounds.size.x,.04f/bounds.size.z)))
            bounds=model.getVisualBounds(root)
            val slot=definitions.take(index).count{it.series==definitions[index].series}
            transform.setPosition(Vector3((slot%3-1)*.052f,.012f-(slot/3)*.060f,0f)-Vector3(bounds.center.x,bounds.min.y,bounds.center.z))
        }
    }
    private var current=-1
    fun sync(series:Int,visible:Boolean){
        root.enabled=visible
        if(series!=current){models.forEachIndexed{index,model->model.enabled=definitions[index].series==series};current=series}
    }
}
